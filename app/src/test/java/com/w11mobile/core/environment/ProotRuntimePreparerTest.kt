package com.w11mobile.core.environment

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProotRuntimePreparerTest {
    @Test
    fun prepare_createsProotTmpAndGuestDirectories() {
        val base = createTempDir("w11mobile")
        try {
            val nativeDir = File(base, "native").apply { mkdirs() }
            File(nativeDir, "libalpine_busybox.so").writeBytes(ByteArray(8) { 1 })
            val paths = AppPaths(base, File(base, "code_cache"), nativeDir.absolutePath)

            ProotRuntimePreparer.prepare(paths)

            assertTrue(paths.prootTmpDir.isDirectory)
            assertTrue(File(paths.rootfsDir, "root").isDirectory)
            assertTrue(File(paths.rootfsDir, "tmp").isDirectory)
            assertTrue(File(paths.rootfsDir, "images").isDirectory)
        } finally {
            base.deleteRecursively()
        }
    }
}
