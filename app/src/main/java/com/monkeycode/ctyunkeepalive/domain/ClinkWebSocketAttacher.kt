package com.monkeycode.ctyunkeepalive.domain

import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.data.LogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap

class ClinkWebSocketAttacher(
    private val client: OkHttpClient,
    private val logRepository: LogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun attachAll(config: ClinkConfig, holdMs: Long, enterWaitMs: Long): Boolean {
        val attacher = SessionAttacher(config, holdMs, enterWaitMs)
        return attacher.attach()
    }

    private inner class SessionAttacher(
        private val config: ClinkConfig,
        private val holdMs: Long,
        private val enterWaitMs: Long,
    ) {
        private val channels = mutableListOf<ClinkChannel>()
        private var sessionId = 0

        suspend fun attach(): Boolean {
            logRepository.append(LogLevel.INFO, "Clink 开始建立多通道: ${config.uri}")
            return try {
                val main = openChannel("MAIN")
                val mainInit = main.waitForType(ClinkProtocol.MAIN_INIT, enterWaitMs)
                if (mainInit.payload.size >= 4) {
                    sessionId = ClinkProtocol.readUInt32LE(mainInit.payload, 0)
                }
                main.sendMessage(ClinkProtocol.MOUSE_MODE_REQUEST, ClinkProtocol.buildMouseModeRequestPayload())
                main.sendMessage(ClinkProtocol.CLIENT2SERVER_CUSTOM, ClinkProtocol.buildCustomJsonPayload(config.userName, config.userId))
                main.sendMessage(ClinkProtocol.CLIENT_LOGIN_INFO, ClinkProtocol.buildLoginInfoPayload(config))

                val display = openChannel("DISPLAY")
                display.sendMessage(ClinkProtocol.DISPLAY_BOOTSTRAP, ClinkProtocol.displayBootstrapPayload())
                display.sendMessage(ClinkProtocol.DISPLAY_INIT, ClinkProtocol.displayInitPayload())
                display.sendMessage(ClinkProtocol.DISPLAY_RESUME, byteArrayOf())

                openChannel("INPUTS")
                openChannel("CURSOR")

                listOf("PLAYBACK", "RECORD", "PORT0", "PORT1", "DATA").forEach { key ->
                    runCatching { openChannel(key) }
                        .onFailure { logRepository.append(LogLevel.WARNING, "Clink 可选通道失败 $key: ${it.message}") }
                }

                delay(holdMs)
                true
            } finally {
                channels.asReversed().forEach { it.close() }
            }
        }

        private suspend fun openChannel(key: String): ClinkChannel {
            val spec = ClinkProtocol.specs.first { it.key == key }
            val channel = ClinkChannel(config, spec) { if (spec.key == "MAIN") 0 else sessionId }
            channels += channel
            channel.connect()
            logRepository.append(LogLevel.INFO, "Clink 通道就绪: ${spec.key}")
            return channel
        }
    }

    private inner class ClinkChannel(
        private val config: ClinkConfig,
        private val spec: ClinkSpec,
        private val sessionIdProvider: () -> Int,
    ) {
        private val url = "${config.uri}/${spec.urlSegment}"
        private val timeoutMs = 15_000L
        private val rawPreReady = ArrayDeque<ByteArray>()
        private val rawQueue = ArrayDeque<ByteArray>()
        private val rawMutex = Mutex()
        private val typeWaiters = ConcurrentHashMap<Int, MutableList<CompletableDeferred<ClinkMessage>>>()
        private val lastMessages = ConcurrentHashMap<Int, ClinkMessage>()
        private var webSocket: WebSocket? = null
        private var ready = false
        private var failed: Throwable? = null
        private var heartbeatJob: Job? = null
        private var closing = false

        suspend fun connect() {
            open()
            sendText(ClinkProtocol.buildHelloPayload(spec, config))
            waitForHandshakeStart()
            sendBuffer(ClinkProtocol.buildLinkMessage(sessionIdProvider(), spec.linkTailHex))
            val reply = waitForReplyPacket()
            sendBuffer(ClinkProtocol.buildAuthPacket(ClinkProtocol.extractPublicKey(reply)))
            waitForAuthAck()
            ready = true
            flushPreReady()
            startHeartbeat()
        }

        suspend fun waitForType(type: Int, timeout: Long): ClinkMessage {
            lastMessages[type]?.let { return it }
            val deferred = CompletableDeferred<ClinkMessage>()
            typeWaiters.compute(type) { _, value -> (value ?: mutableListOf()).also { it += deferred } }
            return withTimeout(timeout) { deferred.await() }
        }

        fun sendMessage(type: Int, payload: ByteArray = byteArrayOf()) {
            sendBuffer(ClinkProtocol.buildMessage(type, payload))
        }

        fun close() {
            closing = true
            heartbeatJob?.cancel()
            runCatching { webSocket?.close(1000, "done") }
            webSocket = null
        }

        private suspend fun open() {
            val opened = CompletableDeferred<Unit>()
            val request = Request.Builder().url(url).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch { onBinary(text.toByteArray()) }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    scope.launch { onBinary(bytes.toByteArray()) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (closing) return
                    fail(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (closing) return
                    fail(IllegalStateException("$url 已关闭($code): $reason"))
                }
            })
            withTimeout(timeoutMs) { opened.await() }
        }

        private suspend fun onBinary(buffer: ByteArray) {
            if (!ready) {
                rawMutex.withLock { rawPreReady.addLast(buffer) }
                return
            }
            handleReadyBuffer(buffer)
        }

        private suspend fun waitForHandshakeStart() {
            while (true) {
                val buffer = takeRaw(timeoutMs, "${spec.key} 握手应答")
                if (buffer.size == 1 && buffer[0].toInt() == 1) return
            }
        }

        private suspend fun waitForReplyPacket(): ByteArray {
            var pending = byteArrayOf()
            while (true) {
                val buffer = takeRaw(timeoutMs, "${spec.key} 握手公钥")
                pending += buffer
                val reply = ClinkProtocol.parseReplyPacket(pending) ?: continue
                if (reply.rest.isNotEmpty()) {
                    rawMutex.withLock { rawQueue.addFirst(reply.rest) }
                }
                return reply.packet
            }
        }

        private suspend fun waitForAuthAck() {
            var pending = byteArrayOf()
            while (true) {
                val buffer = takeRaw(timeoutMs, "${spec.key} 鉴权确认")
                pending += buffer
                if (pending.size >= 4 && ClinkProtocol.readUInt32LE(pending, 0) == 0) return
            }
        }

        private suspend fun flushPreReady() {
            while (true) {
                val next = rawMutex.withLock {
                    if (rawPreReady.isEmpty()) null else rawPreReady.removeFirst()
                } ?: break
                handleReadyBuffer(next)
            }
        }

        private suspend fun handleReadyBuffer(buffer: ByteArray) {
            val reply = ClinkProtocol.parseReplyPacket(buffer)
            if (reply != null) {
                if (reply.rest.isNotEmpty()) handleReadyBuffer(reply.rest)
                return
            }
            val messages = ClinkProtocol.parseMessages(buffer)
            messages.forEach { message ->
                lastMessages[message.type] = message
                typeWaiters.remove(message.type)?.forEach { it.complete(message) }
                when {
                    message.type == 3 && message.payload.size >= 4 -> sendMessage(ClinkProtocol.ACK_SYNC, ClinkProtocol.buildAckSyncPayload(message.payload))
                    message.type == ClinkProtocol.PING -> sendMessage(ClinkProtocol.PONG, message.payload)
                    message.type == ClinkProtocol.HEARTBEAT -> sendMessage(ClinkProtocol.HEARTBEAT_RES, message.payload)
                }
            }
        }

        private fun startHeartbeat() {
            if (heartbeatJob?.isActive == true) return
            heartbeatJob = scope.launch {
                while (true) {
                    delay(30_000L)
                    if (failed != null || !ready) break
                    runCatching { sendMessage(ClinkProtocol.HEARTBEAT) }
                        .onFailure { fail(it) }
                }
            }
        }

        private suspend fun takeRaw(timeout: Long, label: String): ByteArray {
            return withTimeout(timeout) {
                var result: ByteArray? = null
                while (result == null) {
                    failed?.let { throw it }
                    val next = rawMutex.withLock {
                        if (rawQueue.isNotEmpty()) {
                            rawQueue.removeFirst()
                        } else if (rawPreReady.isNotEmpty()) {
                            rawPreReady.removeFirst()
                        } else {
                            null
                        }
                    }
                    if (next != null) {
                        result = next
                    } else {
                        delay(20)
                    }
                }
                result
            }
        }

        private fun sendText(text: String) {
            ensureOpen()
            webSocket?.send(text)
        }

        private fun sendBuffer(buffer: ByteArray) {
            ensureOpen()
            webSocket?.send(ByteString.of(*buffer))
        }

        private fun ensureOpen() {
            failed?.let { throw it }
            check(webSocket != null) { "${spec.key} WebSocket 未处于可发送状态" }
        }

        private fun fail(error: Throwable) {
            if (failed != null) return
            failed = error
            heartbeatJob?.cancel()
            typeWaiters.values.flatten().forEach { it.completeExceptionally(error) }
            typeWaiters.clear()
            logRepository.append(LogLevel.ERROR, "Clink ${spec.key} 失败: ${error.message}")
        }
    }
}
