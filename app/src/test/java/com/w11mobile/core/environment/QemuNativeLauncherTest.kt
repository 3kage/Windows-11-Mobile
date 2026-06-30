package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QemuNativeLauncherTest {
    @Test
    fun buildInvocation_usesLinker64AndNativeLib() {
        val lib = File("/data/app/lib/arm64/libqemu.so")
        val args = listOf("--version")

        val invocation = QemuNativeLauncher.buildInvocation(lib, args)

        assertEquals("/system/bin/linker64", invocation[0])
        assertEquals(lib.absolutePath, invocation[1])
        assertEquals("--version", invocation[2])
    }

    @Test
    fun buildArm64IsoArguments_matchesDirectLaunchContract() {
        val uefi = File("/data/data/com.w11mobile/firmware/QEMU_EFI.fd")
        val iso = File("/data/data/com.w11mobile/files/images/windows.iso")

        val args = QemuNativeLauncher.buildArm64IsoArguments(uefi, iso)

        assertEquals(listOf("virt"), args.slice(args.indexOf("-machine") + 1..args.indexOf("-machine") + 1))
        assertTrue(args.containsAll(listOf("-cpu", "max", "-smp", "4", "-m", "4096")))
        assertEquals(uefi.absolutePath, args[args.indexOf("-bios") + 1])
        assertEquals(iso.absolutePath, args[args.indexOf("-cdrom") + 1])
    }

    @Test
    fun buildEnvironment_setsLdLibraryPath() {
        val paths = AppPaths(
            filesDir = File("/data/data/com.w11mobile/files"),
            applicationCacheDir = File("/data/data/com.w11mobile/cache"),
            codeCacheDir = File("/data/data/com.w11mobile/code_cache"),
            nativeLibraryDir = "/data/app/lib/arm64",
        )

        val env = QemuNativeLauncher.buildEnvironment(paths)

        assertEquals(paths.libDir.absolutePath, env["LD_LIBRARY_PATH"])
        assertEquals(ShellExecutor.LINUX_PATH, env["PATH"])
    }
}
