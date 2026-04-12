package com.monkeycode.ctyunkeepalive.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import java.io.File

object SmartKeepAliveTracker {
    private const val INPUT_FILE = "ctyun-smart-input.ts"
    private const val USB_FILE = "ctyun-smart-usb.ts"

    fun recordInputActivity(context: Context) {
        writeTimestamp(File(context.filesDir, INPUT_FILE))
    }

    fun recordUsbActivity(context: Context) {
        writeTimestamp(File(context.filesDir, USB_FILE))
    }

    fun lastInputActivityAt(context: Context): Long = readTimestamp(File(context.filesDir, INPUT_FILE))

    fun lastUsbActivityAt(context: Context): Long = readTimestamp(File(context.filesDir, USB_FILE))

    fun isAccessibilityEnabled(context: Context): Boolean {
        val service = ComponentName(context, SmartKeepAliveAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(service, ignoreCase = true) }
    }

    private fun writeTimestamp(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(System.currentTimeMillis().toString())
    }

    private fun readTimestamp(file: File): Long {
        return file.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
    }
}
