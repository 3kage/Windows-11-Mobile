package com.w11mobile.core.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RootfsSymlinkMaterializerTest {
    @Test
    fun materialize_doesNotCopyBusyboxForNonEssentialApplet() {
        val root = createTempDir("rootfs")
        try {
            File(root, "bin").mkdirs()
            File(root, "usr/bin").mkdirs()
            File(root, "bin/busybox").writeBytes(ByteArray(8) { 1 })

            val target = File(root, "usr/bin/yes")
            RootfsSymlinkMaterializer.materialize(
                root,
                listOf(target to "/bin/busybox"),
            )

            if (Files.isRegularFile(target.toPath())) {
                assertFalse(Files.size(target.toPath()) == 8L)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun materialize_copiesEssentialShellWhenSymlinkCannotBeCreated() {
        val root = createTempDir("rootfs")
        try {
            File(root, "bin").mkdirs()
            File(root, "bin/busybox").writeBytes(ByteArray(16) { 2 })

            val shell = File(root, "bin/sh")
            RootfsSymlinkMaterializer.materialize(
                root,
                listOf(shell to "/bin/busybox"),
            )

            assertTrue(RootfsEssentials.isReady(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
