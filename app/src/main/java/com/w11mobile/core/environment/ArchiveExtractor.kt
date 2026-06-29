package com.w11mobile.core.environment

import android.system.Os
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

object ArchiveExtractor {

    fun extractTarGz(archive: File, destination: File) {
        destination.mkdirs()
        FileInputStream(archive).use { fileInput ->
            GZIPInputStream(fileInput).use { gzipInput ->
                TarArchiveInputStream(gzipInput).use { tarInput ->
                    extractTarEntries(tarInput, destination, stripPrefix = "")
                }
            }
        }
    }

    fun extractTermuxDeb(debFile: File, termuxPrefix: File) {
        val termuxRoot = termuxPrefix.parentFile ?: error("Невірний шлях Termux prefix")
        termuxRoot.mkdirs()

        FileInputStream(debFile).use { fileInput ->
            ArArchiveInputStream(fileInput).use { arInput ->
                var entry = arInput.nextArEntry
                while (entry != null) {
                    if (entry.name.startsWith("data.tar")) {
                        val payloadStream = when {
                            entry.name.endsWith(".xz") -> XZCompressorInputStream(arInput)
                            entry.name.endsWith(".gz") -> GZIPInputStream(arInput)
                            else -> arInput
                        }
                        TarArchiveInputStream(payloadStream).use { tarInput ->
                            extractTarEntries(
                                tarInput = tarInput,
                                destination = termuxRoot,
                                stripPrefix = "data/data/com.termux/files/",
                            )
                        }
                        return
                    }
                    entry = arInput.nextArEntry
                }
            }
        }
        error("data.tar не знайдено в ${debFile.name}")
    }

    private fun normalizeEntryPath(name: String): String {
        var path = name.trim()
        while (path.startsWith("./")) {
            path = path.removePrefix("./")
        }
        return path
    }

    private fun stripTermuxPrefix(path: String): String {
        val prefixes = listOf(
            "data/data/com.termux/files/",
            "data/data/com.termux/files",
        )
        for (prefix in prefixes) {
            if (path.startsWith(prefix)) {
                return path.removePrefix(prefix).trimStart('/')
            }
        }
        return path
    }

    private fun extractTarEntries(
        tarInput: TarArchiveInputStream,
        destination: File,
        stripPrefix: String,
    ) {
        val pendingSymlinks = mutableListOf<Pair<File, String>>()
        var entry = tarInput.nextEntry
        while (entry != null) {
            val normalized = normalizeEntryPath(entry.name)
            val relativePath = when {
                stripPrefix.isBlank() -> normalized
                else -> {
                    val stripped = normalized.removePrefix(stripPrefix).trimStart('/')
                    if (stripped != normalized) stripped else stripTermuxPrefix(normalized)
                }
            }
            if (relativePath.isNotBlank() && relativePath != "." && relativePath != "/") {
                val target = File(destination, relativePath)
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry is TarArchiveEntry && entry.isSymbolicLink -> {
                        target.parentFile?.mkdirs()
                        pendingSymlinks += target to entry.linkName
                    }
                    else -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            tarInput.copyTo(output)
                        }
                    }
                }
            }
            entry = tarInput.nextEntry
        }

        materializePendingSymlinks(destination, pendingSymlinks)
    }

    private fun materializePendingSymlinks(
        root: File,
        pendingSymlinks: List<Pair<File, String>>,
    ) {
        var remaining = pendingSymlinks
        repeat(8) {
            if (remaining.isEmpty()) return
            val unresolved = mutableListOf<Pair<File, String>>()
            remaining.forEach { (target, linkName) ->
                if (!materializeSymlink(root, target, linkName)) {
                    unresolved += target to linkName
                }
            }
            remaining = unresolved
        }

        remaining.forEach { (target, _) ->
            if (target.exists() && target.length() == 0L) {
                target.delete()
            }
        }
    }

    private fun materializeSymlink(root: File, target: File, linkName: String): Boolean {
        if (target.exists() && target.length() > 0L) return true

        if (target.exists()) target.delete()

        runCatching {
            Os.symlink(linkName, target.absolutePath)
        }.onSuccess {
            return target.exists()
        }

        val resolved = resolveSymlinkTarget(root, target, linkName)
        if (resolved.isFile && resolved.length() > 0L) {
            resolved.copyTo(target, overwrite = true)
            return target.length() > 0L
        }

        return false
    }

    private fun resolveSymlinkTarget(root: File, target: File, linkName: String): File =
        if (linkName.startsWith("/")) {
            File(root, linkName.removePrefix("/"))
        } else {
            File(target.parentFile, linkName)
        }
}
