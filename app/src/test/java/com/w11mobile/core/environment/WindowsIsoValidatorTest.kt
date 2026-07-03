package com.w11mobile.core.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class WindowsIsoValidatorTest {
    @Test
    fun validate_rejectsSmallFile() {
        val file = File.createTempFile("tiny", ".iso").apply {
            writeBytes(ByteArray(1024))
            deleteOnExit()
        }

        val result = WindowsIsoValidator.validate(file)

        assertFalse(result.ok)
        assertTrue(result.message.contains("занадто малий"))
    }

    @Test
    fun validate_acceptsBootableIsoHeader() {
        val file = File.createTempFile("win11", ".iso").apply {
            RandomAccessFile(this, "rw").use { raf ->
                raf.setLength(4L * 1024 * 1024 * 1024)
                val sector = ByteArray(512)
                "CD001".toByteArray().copyInto(sector, destinationOffset = 0x40)
                raf.write(sector)
            }
            deleteOnExit()
        }

        val result = WindowsIsoValidator.validate(file)

        assertTrue(result.ok)
    }
}
