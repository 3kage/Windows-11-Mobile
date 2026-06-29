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
        if (isProotReady()) {
            SharedLibraryMaterializer.materialize(paths.libDir)
            if (isProotReady()) {
                ExecutablePreparer.sealForExecution(paths.proot)
                ExecutablePreparer.sealForExecution(paths.prootLoader)
                return
            }
        }

        purgeCorruptedInstall()

        val packages = packageResolver.resolveInstallOrder("proot")
        for (packageName in packages) {
            val debFile = File(paths.cacheDir, "$packageName.deb")
            val packageUrl = packageResolver.resolveDebUrl(packageName)
            downloadManager.download(packageUrl, debFile, onProgress)
            ArchiveExtractor.extractTermuxDeb(debFile, paths.termuxPrefix)
            debFile.delete()
        }

        SharedLibraryMaterializer.materialize(paths.libDir)

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

        require(isSharedLibraryReady(File(paths.libDir, "libtalloc.so.2"))) {
            "libtalloc.so.2 пошкоджено або порожнє в ${paths.libDir.absolutePath}"
        }

        ExecutablePreparer.installExecutable(paths.extractedProot, paths.proot)
        ExecutablePreparer.installExecutable(extractedLoader, paths.prootLoader)
    }

    fun isProotReady(): Boolean =
        paths.proot.exists() &&
            paths.prootLoader.exists() &&
            isSharedLibraryReady(File(paths.libDir, "libtalloc.so.2")) &&
            isSharedLibraryReady(File(paths.libDir, "libandroid-shmem.so"))

    private fun isSharedLibraryReady(library: File): Boolean =
        library.exists() && library.isFile && library.length() > 0L

    private fun purgeCorruptedInstall() {
        paths.libDir.listFiles()
            ?.filter { it.isFile && it.length() == 0L }
            ?.forEach { it.delete() }

        if (!isSharedLibraryReady(File(paths.libDir, "libtalloc.so.2"))) {
            paths.proot.delete()
            paths.prootLoader.delete()
        }
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
