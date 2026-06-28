package com.w11mobile.core.environment

import java.io.File
import java.security.MessageDigest

class WindowsImageManager(
    private val paths: AppPaths,
    private val downloadManager: DownloadManager,
) {
    data class Meta(
        val source: String,
        val sizeBytes: Long,
        val bootMode: String?,
        val sha256: String?,
    )

    fun isDownloadedForUrl(url: String): Boolean {
        val meta = readMeta() ?: return false
        if (meta.source != url) return false
        return hasStoredImage(meta)
    }

    fun isDownloadedForLocal(uri: String): Boolean {
        val meta = readMeta() ?: return false
        if (meta.source != "local:$uri") return false
        return hasStoredImage(meta)
    }

    private fun hasStoredImage(meta: Meta): Boolean {
        if (meta.bootMode == "iso") {
            return paths.windowsIso.exists()
        }
        return paths.windowsImage.exists()
    }

    suspend fun download(
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File {
        require(url.isNotBlank()) {
            "URL образу Windows не вказано. Введіть посилання на .qcow2 або .iso файл."
        }

        if (isDownloadedForUrl(url)) {
            return paths.windowsImage
        }

        val tempFile = File(paths.cacheDir, "windows.download")
        downloadManager.download(url, tempFile, onProgress)

        if (url.endsWith(".iso", ignoreCase = true)) {
            convertIsoToQcow2(tempFile, paths.windowsImage)
            tempFile.delete()
        } else {
            tempFile.renameTo(paths.windowsImage)
        }

        writeMeta(
            Meta(
                source = url,
                sizeBytes = paths.windowsImage.length(),
                bootMode = "qcow2",
                sha256 = sha256(paths.windowsImage),
            ),
        )
        return paths.windowsImage
    }

    private fun convertIsoToQcow2(isoFile: File, qcow2File: File) {
        qcow2File.parentFile?.mkdirs()
        if (qcow2File.exists()) qcow2File.delete()
        isoFile.copyTo(qcow2File, overwrite = true)
    }

    private fun readMeta(): Meta? {
        if (!paths.windowsImageMeta.exists()) return null
        val lines = paths.windowsImageMeta.readLines()
        val map = lines.mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        val source = map["source"] ?: map["url"] ?: return null
        val size = map["size"]?.toLongOrNull() ?: return null
        return Meta(
            source = source,
            sizeBytes = size,
            bootMode = map["boot"],
            sha256 = map["sha256"],
        )
    }

    private fun writeMeta(meta: Meta) {
        paths.windowsImageMeta.writeText(
            buildString {
                appendLine("source=${meta.source}")
                appendLine("size=${meta.sizeBytes}")
                appendLine("sha256=${meta.sha256.orEmpty()}")
            },
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
