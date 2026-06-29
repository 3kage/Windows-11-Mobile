package com.w11mobile.core.environment

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GuestBusyboxStagerTest {
    @Test
    fun ensureReady_stagesBusyboxFromRootfsWhenNativeLibMissing() {
        val base = createTempDir("w11")
        try {
            val paths = AppPaths(
                base,
                File(base, "code_cache"),
                File(base, "missing_native").absolutePath,
            )
            File(paths.rootfsDir, "bin").mkdirs()
            File(paths.rootfsDir, "bin/busybox").writeBytes(ByteArray(32) { 3 })

            val resolved = GuestBusyboxStager.ensureReady(paths)

            assertTrue(resolved.exists())
            assertTrue(resolved.canExecute())
        } finally {
            base.deleteRecursively()
        }
    }
}
