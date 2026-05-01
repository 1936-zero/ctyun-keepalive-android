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
import kotlin.math.abs
import kotlin.math.max

object SmartKeepAliveTracker {
    private const val SENSOR_FILE = "ctyun-smart-sensor.ts"
    private const val SENSOR_WRITE_THROTTLE_MS = 5_000L
    private const val SENSOR_AXIS_DELTA = 0.02f
    private val sensorTypes = listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_ROTATION_VECTOR,
    )
    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null
    private var lastWriteAt = 0L
    private var lastX: Float? = null
    private var lastY: Float? = null
    private var lastZ: Float? = null

    @Synchronized
    fun startSensorMonitor(context: Context, logRepository: LogRepository? = null): Int {
        if (listener != null) return sensorManager?.let { manager ->
            sensorTypes.count { manager.getDefaultSensor(it) != null }
        } ?: 0
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return 0
        sensorManager = manager
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                if (values.size < 3) return
                val x = values[0]
                val y = values[1]
                val z = values[2]
                if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
                if (!hasMeaningfulAxisData(x, y, z)) return
                recordSensorActivity(appContext, event.sensor?.name.orEmpty(), x, y, z, logRepository)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = sensorListener
        return sensorTypes.mapNotNull { manager.getDefaultSensor(it) }.distinctBy { it.type }.count { sensor ->
            manager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    @Synchronized
    fun stopSensorMonitor() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        sensorManager = null
        lastX = null
        lastY = null
        lastZ = null
    }

    fun lastSensorActivityAt(context: Context): Long = readTimestamp(File(context.filesDir, SENSOR_FILE))

    private fun recordSensorActivity(context: Context, sensorName: String, x: Float, y: Float, z: Float, logRepository: LogRepository?) {
        val now = System.currentTimeMillis()
        if (now - lastWriteAt < SENSOR_WRITE_THROTTLE_MS) return
        lastWriteAt = now
        writeTimestamp(File(context.filesDir, SENSOR_FILE), now)
        logRepository?.append(
            LogLevel.DEBUG,
            "智能保活检测到传感器 xyz 数据: sensor=${sensorName.ifBlank { "unknown" }} x=${axisText(x)} y=${axisText(y)} z=${axisText(z)}",
        )
    }

    private fun hasMeaningfulAxisData(x: Float, y: Float, z: Float): Boolean {
        val previousX = lastX
        val previousY = lastY
        val previousZ = lastZ
        lastX = x
        lastY = y
        lastZ = z
        if (previousX == null || previousY == null || previousZ == null) return true
        val delta = max(max(abs(x - previousX), abs(y - previousY)), abs(z - previousZ))
        return delta >= SENSOR_AXIS_DELTA
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
