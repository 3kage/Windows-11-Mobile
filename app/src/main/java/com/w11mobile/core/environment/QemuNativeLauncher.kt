package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import java.io.File

object QemuNativeLauncher {
    const val LINKER64 = "/system/bin/linker64"
    private const val VIRTIO_ROM = "efi-virtio.rom"

    fun buildEnvironment(paths: AppPaths): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to paths.libDir.absolutePath,
        "HOME" to paths.baseDir.absolutePath,
        "TMPDIR" to paths.appCacheDir.absolutePath,
        "PATH" to ShellExecutor.LINUX_PATH,
        "QEMU_FIRMWARE_PATH" to paths.qemuShareDir.absolutePath,
    )

    fun buildInvocation(qemuLib: File, args: List<String>): List<String> =
        ShellExecutor.buildNativeBinaryInvocation(qemuLib, args)

    fun buildArm64IsoArguments(
        uefiFirmware: File,
        isoFile: File,
        qemuShareDir: File,
        installDisk: File? = null,
    ): List<String> {
        val virtioRom = File(qemuShareDir, VIRTIO_ROM).absolutePath
        return buildList {
            add("-L")
            add(qemuShareDir.absolutePath)
            add("-machine")
            add("virt")
            add("-cpu")
            add("max")
            add("-smp")
            add("4")
            add("-m")
            add("4096")
            add("-bios")
            add(uefiFirmware.absolutePath)
            add("-device")
            add("qemu-xhci,id=usbctrl")
            add("-drive")
            add("file=${isoFile.absolutePath},if=none,id=winiso,media=cdrom")
            add("-device")
            add("usb-storage,bus=usbctrl.0,drive=winiso,bootindex=1")
            if (installDisk != null && installDisk.exists()) {
                add("-drive")
                add("file=${installDisk.absolutePath},if=none,format=qcow2,id=windisk")
                add("-device")
                add("virtio-blk-pci,drive=windisk,bootindex=2,romfile=$virtioRom")
            }
            add("-display")
            add("none")
            add("-serial")
            add("mon:stdio")
            add("-no-reboot")
        }
    }

    fun buildArm64Qcow2Arguments(
        uefiFirmware: File,
        diskFile: File,
        qemuShareDir: File,
    ): List<String> {
        val virtioRom = File(qemuShareDir, VIRTIO_ROM).absolutePath
        return buildList {
            add("-L")
            add(qemuShareDir.absolutePath)
            add("-machine")
            add("virt")
            add("-cpu")
            add("max")
            add("-smp")
            add("4")
            add("-m")
            add("4096")
            add("-bios")
            add(uefiFirmware.absolutePath)
            add("-drive")
            add("file=${diskFile.absolutePath},if=none,format=qcow2,id=windisk")
            add("-device")
            add("virtio-blk-pci,drive=windisk,bootindex=1,romfile=$virtioRom")
            add("-display")
            add("none")
            add("-serial")
            add("mon:stdio")
            add("-no-reboot")
        }
    }
}
