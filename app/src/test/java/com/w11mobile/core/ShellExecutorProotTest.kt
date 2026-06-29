package com.w11mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShellExecutorProotTest {
    @Test
    fun buildProotEnvironment_setsRequiredVariables() {
        val cache = createTempDir("cache")
        try {
            val env = ShellExecutor.buildProotEnvironment(
                appCacheDir = cache,
                prootLoaderPath = "/data/app/libproot_loader.so",
                ldLibraryPath = "/data/app/lib",
            )

            assertEquals("1", env["PROOT_NO_SECCOMP"])
            assertTrue(env["PROOT_TMPDIR"]!!.startsWith(cache.absolutePath))
            assertEquals(env["PROOT_TMPDIR"], env["PROOT_TMP_DIR"])
            assertEquals("/data/app/libproot_loader.so", env["PROOT_LOADER"])
            assertEquals(ShellExecutor.LINUX_PATH, env["PATH"])
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun buildProotArguments_includesSystemBindsAndGuestShell() {
        val rootfs = createTempDir("rootfs")
        val lib = File(createTempDir("native"), "libproot.so").apply { writeBytes(byteArrayOf(1)) }
        try {
            val args = ShellExecutor.buildProotArguments(
                ShellExecutor.ProotLaunchRequest(
                    prootNativeLib = lib,
                    rootfsDir = rootfs,
                    guestCommand = "echo ok",
                ),
            )

            assertTrue(args.containsAll(listOf("-b", "/system", "-b", "/dev", "-b", "/proc", "-b", "/sys")))
            assertEquals("/bin/sh", args[args.lastIndexOf("-r") + 4])
            assertEquals("echo ok", args.last())
        } finally {
            lib.parentFile?.deleteRecursively()
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun buildNativeProotInvocation_usesLinker64AndNativeLib() {
        val lib = File(createTempDir("native"), "libproot.so").apply { writeBytes(byteArrayOf(1)) }
        val rootfs = createTempDir("rootfs")
        try {
            val args = ShellExecutor.buildNativeProotInvocation(
                ShellExecutor.ProotLaunchRequest(
                    prootNativeLib = lib,
                    rootfsDir = rootfs,
                    guestCommand = "true",
                ),
            )
            assertEquals("/system/bin/linker64", args[0])
            assertEquals(lib.absolutePath, args[1])
            assertEquals("/bin/sh", args[args.indexOf("/bin/sh")])
        } finally {
            lib.parentFile?.deleteRecursively()
            rootfs.deleteRecursively()
        }
    }
}
