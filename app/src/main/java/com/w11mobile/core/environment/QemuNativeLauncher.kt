package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import java.io.File

object QemuNativeLauncher {
    const val LINKER64 = "/system/bin/linker64"
    /** QEMU VNC display index (:0) — not the TCP port. */
    const val VNC_DISPLAY_INDEX = 0
    /** TCP port for [VNC_DISPLAY] index :0 — always 5900, never the display index. */
    const val VNC_TCP_PORT = 5900
    /** @see VNC_TCP_PORT */
    const val VNC_PORT = VNC_TCP_PORT
    const val VNC_HOST = "127.0.0.1"
    const val VNC_DISPLAY = "vnc=$VNC_HOST:$VNC_DISPLAY_INDEX"
    const val MONITOR_HOST = "127.0.0.1"
    const val MONITOR_PORT = 4444

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
        val isoDrive =
            "file=${isoFile.absolutePath},if=none,id=winiso,format=raw," +
                "readonly=on,cache=unsafe,aio=threads"
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
            add("-object")
            add("iothread,id=winio")
            add("-device")
            add("qemu-xhci,id=usbctrl,iothread=winio")
            add("-drive")
            add(isoDrive)
            // USB CD-ROM boots UDF Win11 ARM64 ISO more reliably than virtio-blk cdrom on phones.
            add("-device")
            add("usb-storage,bus=usbctrl.0,drive=winiso,bootindex=1,removable=on")
            if (installDisk != null && installDisk.exists()) {
                add("-drive")
                add(
                    "file=${installDisk.absolutePath},if=none,format=qcow2,id=windisk," +
                        "cache=unsafe,aio=threads",
                )
                // Data disk for the installer — no bootindex so UEFI only boots the ISO.
                add("-device")
                add("virtio-blk-pci,drive=windisk,romfile=$virtioRom,iothread=winio")
            }
            add("-boot")
            add("order=c,menu=on,splash-time=60000")
            add("-fw_cfg")
            add("name=opt/org.tianocore/WaitForVMBootTimeout,string=60000000000")
            addUsbInputAndVncDisplay()
            add("-monitor")
            add("tcp:$MONITOR_HOST:$MONITOR_PORT,server,nowait")
            add("-serial")
            add("stdio")
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
            add("-device")
            add("qemu-xhci,id=usbctrl")
            add("-drive")
            add("file=${diskFile.absolutePath},if=none,format=qcow2,id=windisk")
            add("-device")
            add("virtio-blk-pci,drive=windisk,bootindex=1,romfile=$virtioRom")
            addUsbInputAndVncDisplay()
            add("-serial")
            add("stdio")
            add("-no-reboot")
        }
    }

    private fun MutableList<String>.addUsbInputAndVncDisplay() {
        add("-device")
        add("ramfb")
        add("-device")
        add("usb-kbd,bus=usbctrl.0")
        add("-device")
        add("usb-tablet,bus=usbctrl.0")
        add("-display")
        add(VNC_DISPLAY)
    }
}
