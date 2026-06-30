package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun buildArm64IsoArguments_usesUsbXhciAndVirtioBlkPci() {
        val uefi = File("/data/data/com.w11mobile/firmware/QEMU_EFI.fd")
        val iso = File("/data/data/com.w11mobile/files/images/windows.iso")
        val romDir = File("/data/data/com.w11mobile/files/qemu/share")
        val disk = File.createTempFile("windows-disk", ".qcow2").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val args = QemuNativeLauncher.buildArm64IsoArguments(uefi, iso, romDir, disk)

        assertEquals(romDir.absolutePath, args[args.indexOf("-L") + 1])
        assertTrue(args.contains("qemu-xhci,id=usbctrl"))
        assertTrue(args.contains("file=${iso.absolutePath},if=none,id=winiso,format=raw"))
        assertFalse(args.any { it.contains("media=cdrom") })
        assertTrue(args.contains("usb-storage,bus=usbctrl.0,drive=winiso,bootindex=1"))
        assertTrue(args.contains("file=${disk.absolutePath},if=none,format=qcow2,id=windisk"))
        assertTrue(args.contains("virtio-blk-pci,drive=windisk,bootindex=2,romfile=${File(romDir, "efi-virtio.rom").absolutePath}"))
        assertTrue(args.contains("usb-tablet,bus=usbctrl.0"))
        assertTrue(args.contains(QemuNativeLauncher.VNC_DISPLAY))
        assertFalse(args.contains("-cdrom"))
        assertFalse(args.contains("none"))
    }

    @Test
    fun buildArm64Qcow2Arguments_usesVncAndUsbTablet() {
        val uefi = File("/firmware/QEMU_EFI.fd")
        val disk = File("/images/windows.qcow2")
        val romDir = File("/qemu/share")

        val args = QemuNativeLauncher.buildArm64Qcow2Arguments(uefi, disk, romDir)

        assertTrue(args.contains("qemu-xhci,id=usbctrl"))
        assertTrue(args.contains("usb-tablet,bus=usbctrl.0"))
        assertTrue(args.contains(QemuNativeLauncher.VNC_DISPLAY))
        assertFalse(args.any { it == "none" })
    }

    @Test
    fun buildArm64IsoArguments_keepsCoreMachineSettings() {
        val uefi = File("/firmware/QEMU_EFI.fd")
        val iso = File("/images/windows.iso")
        val romDir = File("/qemu/share")

        val args = QemuNativeLauncher.buildArm64IsoArguments(uefi, iso, romDir)

        assertEquals("virt", args[args.indexOf("-machine") + 1])
        assertTrue(args.containsAll(listOf("-cpu", "max", "-smp", "4", "-m", "4096")))
        assertEquals(uefi.absolutePath, args[args.indexOf("-bios") + 1])
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
        assertEquals(paths.qemuShareDir.absolutePath, env["QEMU_FIRMWARE_PATH"])
        assertEquals(ShellExecutor.LINUX_PATH, env["PATH"])
    }
}
