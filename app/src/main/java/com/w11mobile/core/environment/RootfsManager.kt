package com.w11mobile.core.environment

import java.io.File

class RootfsManager(
    private val paths: AppPaths,
    private val downloadManager: DownloadManager,
) {
    suspend fun installAlpineRootfs(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        if (isRootfsReady()) {
            configureRootfs()
            return
        }

        paths.rootfsDir.deleteRecursively()
        paths.rootfsDir.mkdirs()

        val archive = File(paths.cacheDir, "alpine-minirootfs.tar.gz")
        downloadManager.download(EnvironmentUrls.ALPINE_ROOTFS_AARCH64, archive, onProgress)
        ArchiveExtractor.extractTarGz(archive, paths.rootfsDir)
        configureRootfs()
        archive.delete()

        require(isRootfsReady()) {
            "Alpine rootfs пошкоджений після розпакування (${paths.rootfsDir.absolutePath})"
        }
    }

    private fun isRootfsReady(): Boolean {
        val shell = File(paths.rootfsDir, "bin/sh")
        return shell.exists() && shell.isFile && shell.length() > 0L
    }

    fun configureRootfs() {
        ProotRuntimePreparer.prepare(paths)
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
