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
            ProotRuntimePreparer.prepare(paths)
            return
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
        ProotRuntimePreparer.prepare(paths)

        require(isSharedLibraryReady(File(paths.libDir, "libtalloc.so.2"))) {
            "libtalloc.so.2 пошкоджено або порожнє в ${paths.libDir.absolutePath}"
        }

        require(isProotReady()) {
            buildString {
                append("PRoot native libraries не готові.\n")
                append("libproot.so: ${paths.prootNativeLib.absolutePath} ")
                append("(exists=${paths.prootNativeLib.exists()})\n")
                append("libproot_loader.so: ${paths.prootLoaderNativeLib.absolutePath} ")
                append("(exists=${paths.prootLoaderNativeLib.exists()})\n")
                append("Перевстановіть APK, зібраний з jniLibs/libproot.so.")
            }
        }
    }

    fun isProotReady(): Boolean =
        isNativeLibraryReady(paths.prootNativeLib) &&
            isNativeLibraryReady(paths.prootLoaderNativeLib) &&
            isSharedLibraryReady(File(paths.libDir, "libtalloc.so.2")) &&
            isSharedLibraryReady(File(paths.libDir, "libandroid-shmem.so"))

    fun findProotLoader(): File? =
        paths.prootLoaderNativeLib.takeIf { isNativeLibraryReady(it) }

    private fun isNativeLibraryReady(library: File): Boolean =
        library.exists() && library.isFile && library.length() > 0L

    private fun isSharedLibraryReady(library: File): Boolean =
        library.exists() && library.isFile && library.length() > 0L

    private fun purgeCorruptedInstall() {
        paths.libDir.listFiles()
            ?.filter { it.isFile && it.length() == 0L }
            ?.forEach { it.delete() }
    }
}
