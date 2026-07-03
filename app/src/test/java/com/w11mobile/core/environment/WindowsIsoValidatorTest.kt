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
    fun validate_acceptsLargeFileWithoutCd001() {
        val file = File.createTempFile("win11-udf", ".iso").apply {
            RandomAccessFile(this, "rw").use { it.setLength(4L * 1024 * 1024 * 1024) }
            deleteOnExit()
        }

        val result = WindowsIsoValidator.validate(file)

        assertTrue(result.ok)
        assertTrue(result.message.contains("ISO готовий"))
    }
}
