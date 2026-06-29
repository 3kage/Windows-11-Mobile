package com.w11mobile.core.environment

import java.io.File

class TermuxGuestBinaryInstaller(
    private val paths: AppPaths,
    private val downloadManager: DownloadManager,
) {
    private val packageResolver = TermuxPackageResolver(paths.cacheDir, downloadManager)

    suspend fun installExecutables(
        packages: List<String>,
        executables: Map<String, String>,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        val packageSet = packages.flatMap { packageResolver.resolveInstallOrder(it) }.distinct()
        for (packageName in packageSet) {
            val debFile = File(paths.cacheDir, "guest-$packageName.deb")
            val packageUrl = packageResolver.resolveDebUrl(packageName)
            downloadManager.download(packageUrl, debFile, onProgress)
            ArchiveExtractor.extractTermuxDeb(debFile, paths.termuxPrefix)
            debFile.delete()
        }

        SharedLibraryMaterializer.materialize(paths.libDir)

        for ((sourceName, guestName) in executables) {
            val source = findBinary(sourceName)
                ?: error("Termux binary $sourceName не знайдено після розпакування")
            val target = File(paths.guestExecDir, guestName)
            ExecutablePreparer.installExecutable(source, target)
        }
    }

    fun isInstalled(required: Collection<String>): Boolean =
        required.all { File(paths.guestExecDir, it).canExecute() }

    private fun findBinary(name: String): File? {
        val candidates = listOf(
            File(paths.binDir, name),
            File(paths.termuxPrefix, "bin/$name"),
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
    }
}
