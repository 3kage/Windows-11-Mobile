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
        RootfsEssentials.repair(paths.rootfsDir)
        archive.delete()

        require(RootfsEssentials.isReady(paths.rootfsDir)) {
            "Alpine rootfs пошкоджений після розпакування (${paths.rootfsDir.absolutePath})"
        }
    }

    private fun isRootfsReady(): Boolean = RootfsEssentials.isReady(paths.rootfsDir)

    fun configureRootfs() {
        ProotRuntimePreparer.prepare(paths)
        RootfsEssentials.repair(paths.rootfsDir)
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
