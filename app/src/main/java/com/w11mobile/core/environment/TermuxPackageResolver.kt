package com.w11mobile.core.environment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TermuxPackageResolver(
    private val cacheDir: File,
    private val downloadManager: DownloadManager,
) {
    private var cachedIndex: String? = null

    suspend fun resolveDebUrl(
        packageName: String,
        architecture: String = "aarch64",
    ): String = withContext(Dispatchers.IO) {
        val filename = parsePackageFilename(loadIndex(architecture), packageName)
            ?: error("Пакет $packageName не знайдено в репозиторії Termux ($architecture)")

        "${EnvironmentUrls.TERMUX_REPO_BASE}/$filename"
    }

    suspend fun resolveInstallOrder(
        rootPackage: String,
        architecture: String = "aarch64",
    ): List<String> = withContext(Dispatchers.IO) {
        val index = loadIndex(architecture)
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val order = mutableListOf<String>()

        fun visit(packageName: String) {
            if (packageName in visited) return
            if (packageName in visiting) return
            visiting.add(packageName)
            parseDepends(index, packageName).forEach { visit(it) }
            visiting.remove(packageName)
            visited.add(packageName)
            order.add(packageName)
        }

        visit(rootPackage)
        order
    }

    private suspend fun loadIndex(architecture: String): String {
        cachedIndex?.let { return it }
        val indexUrl =
            "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-$architecture/Packages"
        val indexFile = File(cacheDir, "termux-$architecture-Packages")
        downloadManager.download(indexUrl, indexFile)
        cachedIndex = indexFile.readText()
        return cachedIndex!!
    }

    private fun parseDepends(index: String, packageName: String): List<String> {
        val block = index.split("\n\n").firstOrNull { block ->
            block.lines().firstOrNull() == "Package: $packageName"
        } ?: return emptyList()

        val dependsLine = block.lines().firstOrNull { it.startsWith("Depends: ") } ?: return emptyList()
        return dependsLine.removePrefix("Depends: ")
            .split(",")
            .map { it.trim().substringBefore(' ') }
            .filter { it.isNotBlank() }
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
