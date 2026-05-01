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
        return runCatching {
            readCurrentLogLines()
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
                runCatching { clearLegacyFiles() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { clearMediaStoreFiles() }
                }
            }
        }
    }

    private fun appendInternal(entry: LogEntry) {
        val text = buildLogLine(entry)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { appendWithMediaStore(text, entry.timestamp) }
        } else {
            appendWithLegacyFile(text, entry.timestamp)
        }
    }

    private fun appendWithMediaStore(text: String, timestamp: Long) {
        val resolver = appContext.contentResolver
        val fileName = dailyFileName(timestamp)
        val existingUri = queryLogFileUri(fileName)

        val targetUri = existingUri ?: resolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaColumns.DISPLAY_NAME, fileName)
                put(MediaColumns.MIME_TYPE, "text/plain")
                put(MediaColumns.RELATIVE_PATH, relativeDirectory)
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

    private fun clearLegacyFiles() {
        legacyDirectory().listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".log")) {
                file.delete()
            }
        }
    }

    private fun clearMediaStoreFiles() {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val ids = resolver.query(
            collection,
            arrayOf(MediaColumns._ID, MediaColumns.RELATIVE_PATH, MediaColumns.DISPLAY_NAME),
            logFolderSelection(),
            logFolderSelectionArgs(),
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)).orEmpty()
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)).orEmpty()
                    if (isManagedLogFile(relativePath, displayName)) {
                        add(cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns._ID)))
                    }
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
            "内部存储/Documents/$folderName"
        } else {
            legacyDirectory().absolutePath
        }
    }

    private fun readCurrentLogLines(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readMediaStoreLogLines()
        } else {
            val file = File(legacyDirectory(), dailyFileName(System.currentTimeMillis()))
            if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
        }
    }

    private fun readMediaStoreLogLines(): List<String> {
        val resolver = appContext.contentResolver
        val fileName = dailyFileName(System.currentTimeMillis())
        val uri = queryLogFileUri(fileName) ?: return emptyList()
        return resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readLines() }.orEmpty()
    }

    private fun queryLogFileUri(fileName: String): android.net.Uri? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return appContext.contentResolver.query(
            collection,
            arrayOf(MediaColumns._ID, MediaColumns.RELATIVE_PATH, MediaColumns.DISPLAY_NAME, MediaColumns.DATE_MODIFIED),
            "${MediaColumns.DISPLAY_NAME}=? AND ${logFolderSelection()}",
            arrayOf(fileName, *logFolderSelectionArgs()),
            "${MediaColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH)).orEmpty()
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)).orEmpty()
                if (isManagedLogFile(relativePath, displayName)) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaColumns._ID))
                    return@use android.net.Uri.withAppendedPath(collection, id.toString())
                }
            }
            null
        }
    }

    private fun logFolderSelection(): String {
        return "(${MediaColumns.RELATIVE_PATH}=? OR ${MediaColumns.RELATIVE_PATH}=? OR ${MediaColumns.RELATIVE_PATH} LIKE ?)"
    }

    private fun logFolderSelectionArgs(): Array<String> {
        val withoutTrailingSlash = relativeDirectory.trimEnd('/')
        return arrayOf(relativeDirectory, withoutTrailingSlash, "%/$folderName/%")
    }

    private fun isManagedLogFile(relativePath: String, displayName: String): Boolean {
        val normalizedPath = relativePath.trimEnd('/')
        return normalizedPath.endsWith("/$folderName") && displayName.endsWith(".log")
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

    companion object {
        private const val folderName = "天翼云保活日志"
        private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val fileNameFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val logPattern = Regex("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) \\[(\\w+)] (.*)$")
    }
}
