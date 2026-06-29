package com.w11mobile.core.environment

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RootfsEssentialsTest {
    @Test
    fun repair_createsShellFromBusyboxWhenMissing() {
        val rootfs = createTempDir("rootfs")
        try {
            File(rootfs, "bin").mkdirs()
            File(rootfs, "bin/busybox").writeBytes(ByteArray(128) { 7 })

            RootfsEssentials.repair(rootfs)

            assertTrue(RootfsEssentials.isReady(rootfs))
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
