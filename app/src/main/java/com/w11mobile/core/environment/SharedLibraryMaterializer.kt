package com.w11mobile.core.environment

import java.io.File

object SharedLibraryMaterializer {
    private val VERSIONED_SO = Regex("""^(.+\.so)((?:\.\d+)+)$""")

    fun materialize(libDir: File) {
        if (!libDir.isDirectory) return

        libDir.listFiles()
            ?.filter { it.isFile && it.length() == 0L }
            ?.forEach { it.delete() }

        libDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.filter { VERSIONED_SO.matches(it.name) }
            ?.forEach { versionedLib ->
                val match = VERSIONED_SO.matchEntire(versionedLib.name) ?: return@forEach
                val baseName = match.groupValues[1]
                val versionParts = match.groupValues[2].trimStart('.').split('.')

                for (index in versionParts.indices) {
                    val aliasName = buildString {
                        append(baseName)
                        append('.')
                        append(versionParts.take(index + 1).joinToString("."))
                    }
                    copyLibrary(versionedLib, File(libDir, aliasName))
                }
                copyLibrary(versionedLib, File(libDir, baseName))
            }
    }

    private fun copyLibrary(source: File, target: File) {
        if (target.exists() && target.length() >= source.length()) return
        source.copyTo(target, overwrite = true)
    }
}
