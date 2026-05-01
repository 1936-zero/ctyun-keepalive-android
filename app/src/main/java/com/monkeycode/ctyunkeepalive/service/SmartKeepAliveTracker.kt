package com.monkeycode.ctyunkeepalive.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.data.LogRepository
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

object SmartKeepAliveTracker {
    private const val SENSOR_FILE = "ctyun-smart-sensor.ts"
    private const val SENSOR_WRITE_THROTTLE_MS = 5_000L
    private const val STATIC_MAGNITUDE_MIN = 9.5f
    private const val STATIC_MAGNITUDE_MAX = 10.5f
    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null
    private var lastWriteAt = 0L
    private var lastRawLogAt = 0L

    @Synchronized
    fun startSensorMonitor(context: Context, logRepository: LogRepository? = null): Int {
        if (listener != null) return sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { 1 } ?: 0
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return 0
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return 0
        sensorManager = manager
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val values = event.values
                if (values.size < 3) return
                val x = values[0]
                val y = values[1]
                val z = values[2]
                if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
                val now = System.currentTimeMillis()
                if (now - lastRawLogAt >= SENSOR_WRITE_THROTTLE_MS) {
                    lastRawLogAt = now
                    logRepository?.append(
                        LogLevel.DEBUG,
                        "传感器原始: x=${axisText(x)} y=${axisText(y)} z=${axisText(z)} magnitude=${axisText(vectorMagnitude(x, y, z))}",
                    )
                }
                if (!hasMeaningfulAxisData(x, y, z)) return
                recordSensorActivity(appContext, event.sensor?.name.orEmpty(), x, y, z, logRepository)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = sensorListener
        return if (manager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)) 1 else 0
    }

    @Synchronized
    fun stopSensorMonitor() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        sensorManager = null
    }

    fun lastSensorActivityAt(context: Context): Long = readTimestamp(File(context.filesDir, SENSOR_FILE))

    private fun recordSensorActivity(context: Context, sensorName: String, x: Float, y: Float, z: Float, logRepository: LogRepository?) {
        val now = System.currentTimeMillis()
        if (now - lastWriteAt < SENSOR_WRITE_THROTTLE_MS) return
        lastWriteAt = now
        writeTimestamp(File(context.filesDir, SENSOR_FILE), now)
        logRepository?.append(
            LogLevel.DEBUG,
            "智能保活检测到加速度传感器运动: sensor=${sensorName.ifBlank { "unknown" }} x=${axisText(x)} y=${axisText(y)} z=${axisText(z)} magnitude=${axisText(vectorMagnitude(x, y, z))}",
        )
    }

    private fun hasMeaningfulAxisData(x: Float, y: Float, z: Float): Boolean {
        val magnitude = vectorMagnitude(x, y, z)
        return magnitude !in STATIC_MAGNITUDE_MIN..STATIC_MAGNITUDE_MAX
    }

    private fun vectorMagnitude(x: Float, y: Float, z: Float): Float {
        return sqrt(x * x + y * y + z * z)
    }

    private fun writeTimestamp(file: File, timestamp: Long) {
        file.parentFile?.mkdirs()
        file.writeText(timestamp.toString())
    }

    private fun readTimestamp(file: File): Long {
        return file.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
    }

    private fun axisText(value: Float): String = String.format(Locale.US, "%.4f", value)
}
