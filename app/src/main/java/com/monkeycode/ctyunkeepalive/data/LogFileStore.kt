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
import androidx.core.content.ContextCompat
import com.monkeycode.ctyunkeepalive.core.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private fun appendInternal(entry: LogEntry) {
        val text = buildLogLine(entry)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appendWithMediaStore(text, entry.timestamp)
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

    private fun buildLogLine(entry: LogEntry): String {
        return "${dateTimeFormatter.format(Date(entry.timestamp))} [${entry.level.name}] ${entry.message}\n"
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
    }
}
