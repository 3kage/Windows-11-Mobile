package com.w11mobile.core.environment

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RootfsPermissionsTest {
    @Test
    fun sealExecutable_marksBusyboxAsExecutable() {
        val rootfs = createTempDir("rootfs")
        try {
            File(rootfs, "bin").mkdirs()
            val busybox = File(rootfs, "bin/busybox")
            busybox.writeBytes(ByteArray(64) { 1 })

            RootfsPermissions.sealExecutable(busybox)

            assertTrue(RootfsPermissions.isExecutableForGuest(busybox))
        } finally {
            rootfs.deleteRecursively()
        }
    }
}
