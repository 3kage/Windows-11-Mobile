package com.w11mobile.core.environment

import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
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

    private fun extractTarEntries(
        tarInput: TarArchiveInputStream,
        destination: File,
        stripPrefix: String,
    ) {
        var entry = tarInput.nextEntry
        while (entry != null) {
            val relativePath = entry.name.removePrefix(stripPrefix)
            if (relativePath.isNotBlank()) {
                val target = File(destination, relativePath)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        tarInput.copyTo(output)
                    }
                }
            }
            entry = tarInput.nextEntry
        }
    }
}
