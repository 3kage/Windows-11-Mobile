package com.w11mobile.core.environment

import java.io.File

class TermuxProotInstaller(
    private val paths: AppPaths,
    private val downloadManager: DownloadManager,
) {
    suspend fun install(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        if (paths.proot.canExecute()) return

        val debFile = File(paths.cacheDir, "proot.deb")
        downloadManager.download(EnvironmentUrls.PROOT_DEB_AARCH64, debFile, onProgress)
        ArchiveExtractor.extractTermuxDeb(debFile, paths.termuxPrefix)

        paths.proot.setExecutable(true, false)
        findProotLoader()?.setExecutable(true, false)
            ?: error("proot-loader не знайдено після розпакування Termux-пакета")

        debFile.delete()
    }

    fun findProotLoader(): File? {
        val candidates = listOf(
            File(paths.libexecDir, "proot-loader"),
            File(paths.libexecDir, "proot-loader64"),
            File(paths.libexecDir, "proot/proot-loader"),
            File(paths.libexecDir, "proot/proot-loader64"),
        )
        return candidates.firstOrNull { it.exists() }
    }
}
