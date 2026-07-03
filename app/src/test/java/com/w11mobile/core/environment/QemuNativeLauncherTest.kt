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
        assertTrue(
            args.contains(
                "file=${iso.absolutePath},if=none,id=winiso,media=cdrom,format=raw," +
                    "readonly=on,cache=unsafe,aio=threads",
            ),
        )
        assertTrue(args.contains("virtio-blk-pci,drive=winiso,bootindex=1,iothread=winio"))
        assertFalse(args.any { it.contains("usb-storage") && it.contains("winiso") })
        assertTrue(
            args.contains(
                "file=${disk.absolutePath},if=none,format=qcow2,id=windisk,cache=unsafe,aio=threads",
            ),
        )
        assertTrue(
            args.contains(
                "virtio-blk-pci,drive=windisk,romfile=${File(romDir, "efi-virtio.rom").absolutePath},iothread=winio",
            ),
        )
        assertFalse(args.any { it.contains("windisk,bootindex") })
        assertTrue(args.contains("order=c,menu=on,splash-time=60000"))
        assertTrue(args.contains("iothread,id=winio"))
        assertTrue(args.contains("ramfb"))
        assertFalse(args.contains("virtio-gpu-pci"))
        assertTrue(args.contains("usb-kbd,bus=usbctrl.0"))
        assertTrue(args.contains("usb-tablet,bus=usbctrl.0"))
        assertTrue(args.contains("-display"))
        assertTrue(args.contains(QemuNativeLauncher.VNC_DISPLAY))
        assertFalse(args.contains("-vnc"))
        assertTrue(args.contains("-monitor"))
        assertTrue(args.contains("tcp:${QemuNativeLauncher.MONITOR_HOST}:${QemuNativeLauncher.MONITOR_PORT},server,nowait"))
        assertTrue(args.contains("stdio"))
        assertFalse(args.contains("mon:stdio"))
        assertFalse(args.contains("-cdrom"))
        assertFalse(args.any { it == "-display none" || it == "none" })
    }

    @Test
    fun buildArm64Qcow2Arguments_usesVncAndUsbTablet() {
        val uefi = File("/firmware/QEMU_EFI.fd")
        val disk = File("/images/windows.qcow2")
        val romDir = File("/qemu/share")

        val args = QemuNativeLauncher.buildArm64Qcow2Arguments(uefi, disk, romDir)

        assertTrue(args.contains("qemu-xhci,id=usbctrl"))
        assertTrue(args.contains("ramfb"))
        assertFalse(args.contains("virtio-gpu-pci"))
        assertTrue(args.contains("usb-kbd,bus=usbctrl.0"))
        assertTrue(args.contains("usb-tablet,bus=usbctrl.0"))
        assertTrue(args.contains("-display"))
        assertTrue(args.contains(QemuNativeLauncher.VNC_DISPLAY))
        assertFalse(args.contains("-vnc"))
        assertFalse(args.contains("-monitor"))
        assertFalse(args.any { it == "-display none" || it == "none" })
    }

    @Test
    fun buildArm64IsoArguments_keepsCoreMachineSettings() {
        val uefi = File("/firmware/QEMU_EFI.fd")
        val iso = File("/images/windows.iso")
        val romDir = File("/qemu/share")

        val args = QemuNativeLauncher.buildArm64IsoArguments(uefi, iso, romDir)

        assertEquals("virt", args[args.indexOf("-machine") + 1])
        assertTrue(args.containsAll(listOf("-cpu", "max", "-smp", "2", "-m", "4096")))
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
