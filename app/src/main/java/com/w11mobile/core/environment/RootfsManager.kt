package com.w11mobile.core.environment

import java.io.File

class RootfsManager(
    private val paths: AppPaths,
    private val downloadManager: DownloadManager,
) {
    suspend fun installAlpineRootfs(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        if (File(paths.rootfsDir, "bin/sh").exists()) return

        val archive = File(paths.cacheDir, "alpine-minirootfs.tar.gz")
        downloadManager.download(EnvironmentUrls.ALPINE_ROOTFS_AARCH64, archive, onProgress)
        ArchiveExtractor.extractTarGz(archive, paths.rootfsDir)
        configureRootfs()
        archive.delete()
    }

    fun configureRootfs() {
        writeFile(File(paths.rootfsDir, "etc/resolv.conf"), "nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        writeFile(
            File(paths.rootfsDir, "etc/apk/repositories"),
            """
            https://dl-cdn.alpinelinux.org/alpine/v3.20/main
            https://dl-cdn.alpinelinux.org/alpine/v3.20/community
            """.trimIndent(),
        )
        File(paths.rootfsDir, "images").delete()
        File(paths.rootfsDir, "images").mkdirs()
    }

    private fun writeFile(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
