package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import java.io.File

object QemuNativeLauncher {
    const val LINKER64 = "/system/bin/linker64"

    fun buildEnvironment(paths: AppPaths): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to paths.libDir.absolutePath,
        "HOME" to paths.baseDir.absolutePath,
        "TMPDIR" to paths.appCacheDir.absolutePath,
        "PATH" to ShellExecutor.LINUX_PATH,
    )

    fun buildInvocation(qemuLib: File, args: List<String>): List<String> =
        ShellExecutor.buildNativeBinaryInvocation(qemuLib, args)

    fun buildArm64IsoArguments(
        uefiFirmware: File,
        isoFile: File,
        qemuShareDir: File,
        installDisk: File? = null,
    ): List<String> = buildList {
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
        add("-cdrom")
        add(isoFile.absolutePath)
        if (installDisk != null && installDisk.exists()) {
            add("-drive")
            add("if=none,file=${installDisk.absolutePath},format=qcow2,id=windisk")
            add("-device")
            add("virtio-blk-device,drive=windisk,bootindex=2")
        }
        add("-display")
        add("none")
        add("-serial")
        add("mon:stdio")
        add("-no-reboot")
    }

    fun buildArm64Qcow2Arguments(
        uefiFirmware: File,
        diskFile: File,
        qemuShareDir: File,
    ): List<String> = buildList {
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
        add("if=none,file=${diskFile.absolutePath},format=qcow2,id=windisk")
        add("-device")
        add("virtio-blk-device,drive=windisk,bootindex=1")
        add("-display")
        add("none")
        add("-serial")
        add("mon:stdio")
        add("-no-reboot")
    }
}
