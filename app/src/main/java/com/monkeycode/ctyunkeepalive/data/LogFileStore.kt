package com.monkeycode.ctyunkeepalive.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.core.content.ContextCompat
import com.monkeycode.ctyunkeepalive.core.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFileStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val relativeDirectory = "${Environment.DIRECTORY_DOCUMENTS}/$folderName/"
    private val displayPathState = MutableStateFlow(buildDisplayPath())

    fun displayPath(): StateFlow<String> = displayPathState.asStateFlow()

    fun append(entry: LogEntry) {
        scope.launch {
            writeLock.withLock {
                runCatching { appendInternal(entry) }
            }
        }
    }

    fun appendBlocking(entry: LogEntry) {
        runBlocking(Dispatchers.IO) {
            writeLock.withLock {
                runCatching { appendInternal(entry) }
            }
        }
    }

    fun readRecentEntries(limit: Int = 200): List<LogEntry> {
        val file = File(backupDirectory(), dailyFileName(System.currentTimeMillis()))
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines(Charsets.UTF_8)
                .takeLast(limit)
                .mapNotNull(::parseLogLine)
        }.getOrDefault(emptyList())
    }

    fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            emptyList()
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun hasRequiredPermissions(context: Context = appContext): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun openLogFolder(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, buildInitialUri())
            }
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun clearAll() {
        runBlocking(Dispatchers.IO) {
            writeLock.withLock {
                runCatching { clearBackupFiles() }
                runCatching { clearLegacyFiles() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { clearMediaStoreFiles() }
                }
            }
        }
    }

    private fun appendInternal(entry: LogEntry) {
        val text = buildLogLine(entry)
        appendWithBackupFile(text, entry.timestamp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { appendWithMediaStore(text, entry.timestamp) }
        } else {
            appendWithLegacyFile(text, entry.timestamp)
        }
    }

    private fun appendWithMediaStore(text: String, timestamp: Long) {
        val resolver = appContext.contentResolver
        val fileName = dailyFileName(timestamp)
        val existingUri = resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(relativeDirectory, fileName),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            android.net.Uri.withAppendedPath(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), id.toString())
        }

        val targetUri = existingUri ?: resolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
            },
        )

        targetUri?.let { uri ->
            resolver.openOutputStream(uri, "wa")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.append(text)
            }
        }
    }

    private fun appendWithLegacyFile(text: String, timestamp: Long) {
        val directory = legacyDirectory().apply { mkdirs() }
        File(directory, dailyFileName(timestamp)).appendText(text, Charsets.UTF_8)
    }

    private fun appendWithBackupFile(text: String, timestamp: Long) {
        val directory = backupDirectory().apply { mkdirs() }
        File(directory, dailyFileName(timestamp)).appendText(text, Charsets.UTF_8)
    }

    private fun clearBackupFiles() {
        backupDirectory().listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".log")) {
                file.writeText("", Charsets.UTF_8)
            }
        }
    }

    private fun clearLegacyFiles() {
        legacyDirectory().listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".log")) {
                file.writeText("", Charsets.UTF_8)
            }
        }
    }

    private fun clearMediaStoreFiles() {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val ids = resolver.query(
            collection,
            arrayOf(MediaColumns._ID),
            "${MediaColumns.RELATIVE_PATH}=?",
            arrayOf(relativeDirectory),
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns._ID)))
                }
            }
        }.orEmpty()
        ids.forEach { id ->
            resolver.delete(android.net.Uri.withAppendedPath(collection, id.toString()), null, null)
        }
    }

    private fun buildLogLine(entry: LogEntry): String {
        return "${dateTimeFormatter.format(Date(entry.timestamp))} [${entry.level.name}] ${entry.message}\n"
    }

    private fun parseLogLine(line: String): LogEntry? {
        val match = logPattern.matchEntire(line.trim()) ?: return null
        val timestamp = runCatching { dateTimeFormatter.parse(match.groupValues[1])?.time }.getOrNull() ?: System.currentTimeMillis()
        val level = runCatching { com.monkeycode.ctyunkeepalive.core.LogLevel.valueOf(match.groupValues[2]) }.getOrNull() ?: return null
        return LogEntry(level = level, message = match.groupValues[3], timestamp = timestamp)
    }

    private fun dailyFileName(timestamp: Long): String {
        return "${fileNameFormatter.format(Date(timestamp))}.log"
    }

    private fun buildDisplayPath(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "内部存储/Documents/$folderName | 备份: ${backupDirectory().absolutePath}"
        } else {
            legacyDirectory().absolutePath
        }
    }

    private fun buildInitialUri(): android.net.Uri {
        return DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Environment.DIRECTORY_DOCUMENTS}/$folderName",
        )
    }

    private fun legacyDirectory(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), folderName)
    }

    private fun backupDirectory(): File {
        return File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), folderName)
    }

    companion object {
        private const val folderName = "天翼云保活日志"
        private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val fileNameFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val logPattern = Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) \\[(\\w+)] (.*)$")
    }
}
