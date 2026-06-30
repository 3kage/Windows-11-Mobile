package com.w11mobile.core.environment

import java.io.File

data class TermuxExecutableSpec(
    val searchNames: List<String>,
    val guestName: String,
)

object TermuxBinaryLocator {
    fun findBinary(searchRoots: List<File>, names: List<String>): File? {
        for (name in names) {
            for (root in searchRoots) {
                directCandidates(root, name).firstOrNull { it.isUsableBinary() }?.let { return it }
                findRecursive(root, name)?.let { return it }
            }
        }
        return null
    }

    fun describeSearchRoots(searchRoots: List<File>): String = buildString {
        for (root in searchRoots) {
            appendLine("${root.absolutePath}:")
            appendLine("  top-level: ${root.list()?.joinToString() ?: "(empty)"}")
            val binDir = File(root, "bin")
            if (binDir.isDirectory) {
                appendLine("  bin/: ${binDir.list()?.joinToString() ?: "(empty)"}")
            }
            val usrBinDir = File(root, "usr/bin")
            if (usrBinDir.isDirectory) {
                appendLine("  usr/bin/: ${usrBinDir.list()?.joinToString() ?: "(empty)"}")
            }
        }
    }.trimEnd()

    private fun directCandidates(root: File, name: String): List<File> = listOf(
        File(root, name),
        File(root, "bin/$name"),
        File(root, "usr/bin/$name"),
    )

    private fun findRecursive(dir: File, name: String, depth: Int = 0): File? {
        if (!dir.isDirectory || depth > 6) return null

        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name == name && file.isUsableBinary()) {
                return file
            }
        }
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                findRecursive(file, name, depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun File.isUsableBinary(): Boolean = isFile && length() > 0L
}
