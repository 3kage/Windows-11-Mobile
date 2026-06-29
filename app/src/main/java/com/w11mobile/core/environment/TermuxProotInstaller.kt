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
        if (paths.proot.exists() && paths.prootLoader.exists()) {
            ExecutablePreparer.sealForExecution(paths.proot)
            ExecutablePreparer.sealForExecution(paths.prootLoader)
            return
        }

        val debFile = File(paths.cacheDir, "proot.deb")
        val prootUrl = packageResolver.resolveDebUrl(packageName = "proot")
        downloadManager.download(prootUrl, debFile, onProgress)
        ArchiveExtractor.extractTermuxDeb(debFile, paths.termuxPrefix)

        require(paths.extractedProot.exists()) {
            "proot не знайдено після розпакування (${paths.extractedProot.absolutePath})"
        }

        val extractedLoader = findExtractedLoader()
            ?: error(
                buildString {
                    append("proot-loader не знайдено після розпакування Termux-пакета.\n")
                    append("Шукали в: ${paths.libexecDir.absolutePath}\n")
                    append("Вміст libexec: ${paths.libexecDir.list()?.joinToString() ?: "порожньо"}")
                },
            )

        ExecutablePreparer.installExecutable(paths.extractedProot, paths.proot)
        ExecutablePreparer.installExecutable(extractedLoader, paths.prootLoader)

        debFile.delete()
    }

    fun findProotLoader(): File? =
        paths.prootLoader.takeIf { it.exists() && it.length() > 0L }

    private fun findExtractedLoader(): File? {
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
