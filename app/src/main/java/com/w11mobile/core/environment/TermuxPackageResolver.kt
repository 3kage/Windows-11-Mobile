package com.w11mobile.core.environment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TermuxPackageResolver(
    private val cacheDir: File,
    private val downloadManager: DownloadManager,
) {
    suspend fun resolveDebUrl(
        packageName: String,
        architecture: String = "aarch64",
    ): String = withContext(Dispatchers.IO) {
        val indexUrl =
            "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-$architecture/Packages"
        val indexFile = File(cacheDir, "termux-$architecture-Packages")
        downloadManager.download(indexUrl, indexFile)

        val filename = parsePackageFilename(indexFile.readText(), packageName)
            ?: error("Пакет $packageName не знайдено в репозиторії Termux ($architecture)")

        "${EnvironmentUrls.TERMUX_REPO_BASE}/$filename"
    }

    private fun parsePackageFilename(packagesIndex: String, packageName: String): String? {
        val blocks = packagesIndex.split("\n\n")
        for (block in blocks) {
            val lines = block.lines()
            if (lines.firstOrNull() == "Package: $packageName") {
                return lines.firstOrNull { it.startsWith("Filename: ") }
                    ?.removePrefix("Filename: ")
                    ?.trim()
            }
        }
        return null
    }
}
