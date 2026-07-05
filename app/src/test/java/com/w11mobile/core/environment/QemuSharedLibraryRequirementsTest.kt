package com.w11mobile.core.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QemuSharedLibraryRequirementsTest {
    @Test
    fun missingLibraries_detectsAbsentLibzstd() {
        val libDir = createTempDir("termux-lib")
        try {
            File(libDir, "libcurl.so").writeText("stub")

            assertFalse(QemuSharedLibraryRequirements.hasRequiredLibraries(libDir))
            assertEquals(listOf("libzstd.so.1"), QemuSharedLibraryRequirements.missingLibraries(libDir))
        } finally {
            libDir.deleteRecursively()
        }
    }

    @Test
    fun hasRequiredLibraries_trueWhenLibzstdPresent() {
        val libDir = createTempDir("termux-lib")
        try {
            File(libDir, "libzstd.so.1").writeBytes(ByteArray(1024) { 1 })

            assertTrue(QemuSharedLibraryRequirements.hasRequiredLibraries(libDir))
            assertTrue(QemuSharedLibraryRequirements.missingLibraries(libDir).isEmpty())
        } finally {
            libDir.deleteRecursively()
        }
    }
}
