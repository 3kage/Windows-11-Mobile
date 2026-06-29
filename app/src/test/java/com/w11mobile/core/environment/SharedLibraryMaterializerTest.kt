package com.w11mobile.core.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedLibraryMaterializerTest {
    @Test
    fun materialize_createsMajorVersionAliasesFromVersionedLibrary() {
        val libDir = createTempDir("termux-lib")
        try {
            val versioned = File(libDir, "libtalloc.so.2.4.3")
            versioned.writeBytes(ByteArray(4096) { it.toByte() })

            SharedLibraryMaterializer.materialize(libDir)

            assertTrue(File(libDir, "libtalloc.so.2.4.3").length() == 4096L)
            assertEquals(4096L, File(libDir, "libtalloc.so.2.4").length())
            assertEquals(4096L, File(libDir, "libtalloc.so.2").length())
            assertEquals(4096L, File(libDir, "libtalloc.so").length())
        } finally {
            libDir.deleteRecursively()
        }
    }

    @Test
    fun materialize_removesEmptyPlaceholderFiles() {
        val libDir = createTempDir("termux-lib")
        try {
            File(libDir, "libtalloc.so.2").writeBytes(ByteArray(0))
            File(libDir, "libtalloc.so.2.4.3").writeBytes(ByteArray(1024) { 1 })

            SharedLibraryMaterializer.materialize(libDir)

            assertEquals(1024L, File(libDir, "libtalloc.so.2").length())
        } finally {
            libDir.deleteRecursively()
        }
    }
}
