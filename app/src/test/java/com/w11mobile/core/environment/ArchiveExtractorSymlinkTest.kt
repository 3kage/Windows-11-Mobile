package com.w11mobile.core.environment

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

class ArchiveExtractorSymlinkTest {
    @Test
    fun extractTarGz_resolvesAbsoluteSymlinksWithinRootfs() {
        val archive = File.createTempFile("alpine-like", ".tar.gz")
        val destination = createTempDir("rootfs")
        try {
            writeMiniAlpineArchive(archive)

            ArchiveExtractor.extractTarGz(archive, destination)

            val busybox = File(destination, "bin/busybox")
            val shell = File(destination, "bin/sh")
            assertTrue(busybox.isFile)
            assertEquals(4L, busybox.length())
            assertTrue(shell.isFile)
            assertEquals(4L, shell.length())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    private fun writeMiniAlpineArchive(archive: File) {
        FileOutputStream(archive).use { fileOutput ->
            GZIPOutputStream(fileOutput).use { gzipOutput ->
                TarArchiveOutputStream(gzipOutput).use { tarOutput ->
                    addFile(tarOutput, "bin/busybox", byteArrayOf(0x01, 0x02, 0x03, 0x04))
                    addSymlink(tarOutput, "bin/sh", "/bin/busybox")
                }
            }
        }
    }

    private fun addFile(tarOutput: TarArchiveOutputStream, path: String, content: ByteArray) {
        val entry = TarArchiveEntry("./$path")
        entry.size = content.size.toLong()
        tarOutput.putArchiveEntry(entry)
        tarOutput.write(content)
        tarOutput.closeArchiveEntry()
    }

    private fun addSymlink(tarOutput: TarArchiveOutputStream, path: String, target: String) {
        val entry = TarArchiveEntry("./$path", TarArchiveEntry.LF_SYMLINK)
        entry.linkName = target
        tarOutput.putArchiveEntry(entry)
        tarOutput.closeArchiveEntry()
    }
}
