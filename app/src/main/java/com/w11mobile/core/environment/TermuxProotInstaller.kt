package com.w11mobile.core.environment

import java.io.File

class TermuxProotInstaller(
    private val paths: AppPaths,
    private val downloadManager: DownloadManager,
) {
    private val packageResolver = TermuxPackageResolver(paths.cacheDir, downloadManager)

    suspend fun install(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        if (paths.proot.canExecute() && findProotLoader()?.canExecute() == true) return

        val debFile = File(paths.cacheDir, "proot.deb")
        val prootUrl = packageResolver.resolveDebUrl(packageName = "proot")
        downloadManager.download(prootUrl, debFile, onProgress)
        ArchiveExtractor.extractTermuxDeb(debFile, paths.termuxPrefix)

        require(paths.proot.exists()) {
            "proot не знайдено після розпакування (${paths.proot.absolutePath})"
        }
        paths.proot.setExecutable(true, false)

        val loader = findProotLoader()
            ?: error(
                buildString {
                    append("proot-loader не знайдено після розпакування Termux-пакета.\n")
                    append("Шукали в: ${paths.libexecDir.absolutePath}\n")
                    append("Вміст libexec: ${paths.libexecDir.list()?.joinToString() ?: "порожньо"}")
                },
            )
        loader.setExecutable(true, false)

        debFile.delete()
    }

    fun findProotLoader(): File? {
        val candidates = listOf(
            File(paths.libexecDir, "proot/loader"),
            File(paths.libexecDir, "proot/loader32"),
            File(paths.libexecDir, "proot-loader"),
            File(paths.libexecDir, "proot-loader64"),
            File(paths.libexecDir, "proot/proot-loader"),
            File(paths.libexecDir, "proot/proot-loader64"),
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
            ?: findLoaderRecursively(paths.libexecDir)
    }

    private fun findLoaderRecursively(directory: File): File? {
        if (!directory.isDirectory) return null
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name.contains("loader", ignoreCase = true)) {
                return file
            }
            if (file.isDirectory) {
                findLoaderRecursively(file)?.let { return it }
            }
        }
        return null
    }
}
