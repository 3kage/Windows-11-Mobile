package com.w11mobile.core.environment

import android.util.Log
import java.io.File

class TermuxGuestBinaryInstaller(
    private val paths: AppPaths,
    private val downloadManager: DownloadManager,
) {
    companion object {
        private const val TAG = "TermuxGuestBinaryInstaller"
    }

    private val packageResolver = TermuxPackageResolver(paths.cacheDir, downloadManager)

    private val searchRoots: List<File>
        get() = listOf(paths.binDir, paths.termuxPrefix, paths.termuxRoot)

    suspend fun installPackageLibraries(
        packages: List<String>,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onLog: (String) -> Unit = {},
    ) {
        val packageSet = packages.flatMap { packageResolver.resolveInstallOrder(it) }.distinct()
        for (packageName in packageSet) {
            onLog("Termux libs: $packageName\n")
            val debFile = File(paths.cacheDir, "guest-$packageName.deb")
            val packageUrl = packageResolver.resolveDebUrl(packageName)
            downloadManager.download(packageUrl, debFile, onProgress)
            ArchiveExtractor.extractTermuxDeb(debFile, paths.termuxPrefix)
            debFile.delete()
        }

        SharedLibraryMaterializer.materialize(paths.libDir)
        onLog("Termux shared libs: ${paths.libDir.absolutePath}\n")
    }

    suspend fun installExecutables(
        packages: List<String>,
        executables: List<TermuxExecutableSpec>,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onLog: (String) -> Unit = {},
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

        for (spec in executables) {
            val source = TermuxBinaryLocator.findBinary(searchRoots, spec.searchNames)
            if (source == null) {
                val diagnostic = TermuxBinaryLocator.describeSearchRoots(searchRoots)
                val message = buildString {
                    append("Termux binary ")
                    append(spec.searchNames.joinToString(" / "))
                    append(" не знайдено після розпакування.\n")
                    append("Структура каталогів:\n")
                    append(diagnostic)
                }
                Log.e(TAG, message)
                onLog("$message\n")
                error("Termux binary ${spec.searchNames.first()} не знайдено після розпакування")
            }

            source.setExecutable(true, false)

            val target = File(paths.guestExecDir, spec.guestName)
            ExecutablePreparer.installExecutable(source, target)
            onLog("QEMU: ${source.absolutePath} → ${target.absolutePath}\n")
        }
    }

    fun isInstalled(required: Collection<String>): Boolean =
        required.all { File(paths.guestExecDir, it).canExecute() }
}
