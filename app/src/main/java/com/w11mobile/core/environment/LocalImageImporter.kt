package com.w11mobile.core.environment

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class LocalImageImporter(
    private val application: Application,
    private val paths: AppPaths,
) {
    suspend fun importFromUri(
        uri: Uri,
        displayName: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: queryDisplayName(uri)
            ?: error("Не вдалося визначити ім'я файлу")

        WindowsImageFileValidator.validateFileName(resolvedName)?.let { message ->
            error(message)
        }

        val totalBytes = querySize(uri).coerceAtLeast(0L)
        val tempFile = File(paths.cacheDir, resolvedName)
        if (tempFile.exists()) tempFile.delete()

        application.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                copyWithProgress(input, output, totalBytes, onProgress)
            }
        } ?: error("Не вдалося відкрити локальний файл")

        tempFile
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            copied += read
            onProgress(copied, totalBytes)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun querySize(uri: Uri): Long {
        application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(index)
            }
        }
        return -1L
    }
}
