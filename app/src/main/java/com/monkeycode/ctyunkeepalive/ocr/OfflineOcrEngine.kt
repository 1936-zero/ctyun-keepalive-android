package com.monkeycode.ctyunkeepalive.ocr

import android.content.Context
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.Python
import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.core.base64NoWrap
import com.monkeycode.ctyunkeepalive.data.LogRepository

class OfflineOcrEngine(
    private val context: Context,
    private val logRepository: LogRepository,
) {
    @Volatile
    private var ready = false

    fun ensureReady(): Boolean {
        return runCatching {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()
            py.getModule("ocr_bridge")
            ready = true
            true
        }.onFailure {
            logRepository.append(LogLevel.ERROR, "Python OCR 初始化失败: ${it.message}")
        }.getOrDefault(false)
    }

    fun isReady(): Boolean = ready

    fun classify(image: ByteArray): String {
        check(ensureReady()) { "OCR 引擎未就绪" }
        val py = Python.getInstance()
        val result = py.getModule("ocr_bridge").callAttr("classify", base64NoWrap(image))
        return result.toString().trim()
    }
}
