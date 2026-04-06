package com.monkeycode.ctyunkeepalive.domain

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

data class ClinkConfig(
    val uri: String,
    val host: String,
    val port: String,
    val serverName: String,
    val token: String,
    val desktopId: Int,
    val deviceType: String,
    val deviceCode: String,
    val userAccount: String,
    val userName: String,
    val userId: Int,
    val clientCert: String,
    val clientKey: String,
    val caCert: String,
    val oqs: Int,
)

data class ClinkSpec(
    val key: String,
    val channelType: Int,
    val channelName: String,
    val urlSegment: String,
    val required: Boolean,
    val linkTailHex: String,
)

data class ClinkPacketResult(
    val packet: ByteArray,
    val rest: ByteArray,
)

data class ClinkMessage(
    val type: Int,
    val size: Int,
    val payload: ByteArray,
)

object ClinkProtocol {
    private val gson = Gson()
    private val derPublicKeyMarker = hexToBytes("30819f300d06092a864886f70d010101050003818d0030818902818100")

    const val ACK_SYNC = 1
    const val PONG = 3
    const val PING = 4
    const val HEARTBEAT = 7
    const val HEARTBEAT_RES = 9
    const val DISPLAY_INIT = 101
    const val MAIN_INIT = 103
    const val MOUSE_MODE_REQUEST = 105
    const val DISPLAY_BOOTSTRAP = 108
    const val DISPLAY_RESUME = 110
    const val CLIENT_LOGIN_INFO = 112
    const val CLIENT2SERVER_CUSTOM = 118

    val specs = listOf(
        ClinkSpec("MAIN", 1, "MAIN", "MAIN", true, "01000100000001000000120000000900000004080000"),
        ClinkSpec("DISPLAY", 2, "DISPLAY", "DISPLAY", true, "020001000000010000001200000009000000114d8808"),
        ClinkSpec("INPUTS", 3, "INPUTS", "INPUTS", true, "030001000000000000001200000009000000"),
        ClinkSpec("CURSOR", 4, "CURSOR", "CURSOR", true, "040001000000000000001200000009000000"),
        ClinkSpec("PLAYBACK", 5, "PLAYBACK", "PLAYBACK", false, "0500010000000100000012000000090000000e000000"),
        ClinkSpec("RECORD", 6, "RECORD", "RECORD", false, "06000100000004000000120000000900000002000000040000000800000010000000"),
        ClinkSpec("PORT0", 10, "PORT", "PORT", false, "0a0001000000000000001200000009000000"),
        ClinkSpec("PORT1", 10, "PORT", "PORT", false, "0a0101000000000000001200000009000000"),
        ClinkSpec("DATA", 12, "DATA", "DATA", false, "0c0001000000000000001200000009000000"),
    )

    fun buildHelloPayload(spec: ClinkSpec, config: ClinkConfig): String {
        return gson.toJson(
            mapOf(
                "type" to spec.channelType,
                "ssl" to 1,
                "host" to config.host,
                "port" to config.port,
                "ca" to config.caCert,
                "cert" to config.clientCert,
                "key" to config.clientKey,
                "servername" to config.serverName,
                "oqs" to config.oqs,
            )
        )
    }

    fun buildLinkMessage(sessionId: Int, linkTailHex: String): ByteArray {
        val tail = hexToBytes(linkTailHex)
        val payload = littleEndian(4) { putInt(sessionId) } + tail
        return buildRedqPacket(payload)
    }

    fun buildAuthPacket(publicKeyDer: ByteArray): ByteArray {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            publicKey,
            OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT),
        )
        val encrypted = cipher.doFinal(byteArrayOf(0))
        return littleEndian(4) { putInt(1) } + encrypted
    }

    fun buildMessage(type: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val head = littleEndian(6) {
            putShort(type.toShort())
            putInt(payload.size)
        }
        return head + payload
    }

    fun buildMouseModeRequestPayload(): ByteArray = littleEndian(2) { putShort(2) }

    fun buildCustomJsonPayload(userName: String, userId: Int): ByteArray {
        val json = gson.toJson(mapOf("type" to 1, "userName" to userName, "userInfo" to "", "userId" to userId))
        val text = json.toByteArray()
        return littleEndian(8) {
            putInt(text.size)
            putInt(8)
        } + text
    }

    fun buildLoginInfoPayload(config: ClinkConfig): ByteArray {
        val token = cString(config.token)
        val deviceType = cString(config.deviceType)
        val deviceCode = cString(config.deviceCode)
        val userAccount = cString(config.userAccount)

        val offsetToken = 36
        val offsetDeviceType = offsetToken + token.size
        val offsetDeviceCode = offsetDeviceType + deviceType.size
        val offsetUserAccount = offsetDeviceCode + deviceCode.size

        val head = littleEndian(36) {
            putInt(config.desktopId)
            putInt(token.size)
            putInt(offsetToken)
            putInt(deviceType.size)
            putInt(offsetDeviceType)
            putInt(deviceCode.size)
            putInt(offsetDeviceCode)
            putInt(userAccount.size)
            putInt(offsetUserAccount)
        }
        return head + token + deviceType + deviceCode + userAccount
    }

    fun buildAckSyncPayload(payload: ByteArray): ByteArray = payload.copyOfRange(0, minOf(4, payload.size))

    fun displayBootstrapPayload(): ByteArray = hexToBytes("0223031903")

    fun displayInitPayload(): ByteArray = hexToBytes("010000a000000000000000000000")

    fun parseReplyPacket(buffer: ByteArray): ClinkPacketResult? {
        if (buffer.size < 16) return null
        val start = indexOf(buffer, "REDQ".toByteArray())
        if (start < 0 || buffer.size < start + 16) return null
        val size = readUInt32LE(buffer, start + 12)
        val end = start + 16 + size
        if (buffer.size < end) return null
        return ClinkPacketResult(
            packet = buffer.copyOfRange(start, end),
            rest = buffer.copyOfRange(end, buffer.size),
        )
    }

    fun extractPublicKey(packet: ByteArray): ByteArray {
        val start = indexOf(packet, derPublicKeyMarker)
        require(start >= 0) { "未从 clink 回复中找到公钥" }
        val totalLength = readDerObjectLength(packet, start)
        return packet.copyOfRange(start, start + totalLength)
    }

    fun parseMessages(buffer: ByteArray): List<ClinkMessage> {
        if (buffer.size < 6) return emptyList()
        if (indexOf(buffer, "REDQ".toByteArray()) == 0) return emptyList()
        val messages = mutableListOf<ClinkMessage>()
        var offset = 0
        while (offset + 6 <= buffer.size) {
            val type = readUInt16LE(buffer, offset)
            val size = readUInt32LE(buffer, offset + 2)
            val end = offset + 6 + size
            if (size < 0 || end < offset + 6 || end > buffer.size) {
                return if (messages.isNotEmpty()) messages else emptyList()
            }
            messages += ClinkMessage(type = type, size = size, payload = buffer.copyOfRange(offset + 6, end))
            offset = end
        }
        return messages
    }

    fun readUInt32LE(buffer: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(buffer, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readUInt16LE(buffer: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(buffer, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
    }

    private fun readDerObjectLength(buffer: ByteArray, offset: Int): Int {
        require(buffer.size >= offset + 2) { "DER 公钥长度不足" }
        val lengthByte = buffer[offset + 1].toInt() and 0xff
        if ((lengthByte and 0x80) == 0) {
            return 2 + lengthByte
        }
        val lengthSize = lengthByte and 0x7f
        require(buffer.size >= offset + 2 + lengthSize) { "DER 公钥长度头不完整" }
        var valueLength = 0
        repeat(lengthSize) { index ->
            valueLength = (valueLength shl 8) or (buffer[offset + 2 + index].toInt() and 0xff)
        }
        return 2 + lengthSize + valueLength
    }

    private fun indexOf(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        for (index in 0..source.size - target.size) {
            var matched = true
            for (subIndex in target.indices) {
                if (source[index + subIndex] != target[subIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) return index
        }
        return -1
    }

    private fun buildRedqPacket(payload: ByteArray): ByteArray {
        val head = ByteArrayOutputStream().apply {
            write("REDQ".toByteArray())
            write(littleEndian(4) { putInt(2) })
            write(littleEndian(4) { putInt(2) })
            write(littleEndian(4) { putInt(payload.size) })
        }.toByteArray()
        return head + payload
    }

    private fun cString(value: String): ByteArray = value.toByteArray() + byteArrayOf(0)

    private fun littleEndian(size: Int, block: ByteBuffer.() -> Unit): ByteArray {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array()
    }

    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
