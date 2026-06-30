package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import java.io.File

class QemuManager(
    private val paths: AppPaths,
    private val prootExecutor: PRootExecutor,
    private val guestBinaryInstaller: TermuxGuestBinaryInstaller,
) {
    companion object {
        const val QEMU_SYSTEM_AARCH64 = "qemu-system-aarch64"
        const val QEMU_SYSTEM_X86_64 = "qemu-system-x86_64"
        const val QEMU_IMG = "qemu-img"
    }

    private val markerFile = File(paths.cacheDir, "qemu_termux_installed.marker")

    private val guestBinaries = listOf(
        QEMU_SYSTEM_AARCH64,
        QEMU_SYSTEM_X86_64,
        QEMU_IMG,
    )

    suspend fun install(onLog: (String) -> Unit): ShellExecutor.Result {
        val legacyMarker = File(paths.cacheDir, "qemu_aarch64_installed.marker")
        if (legacyMarker.exists()) {
            onLog("Оновлення QEMU до Termux/Android-сумісної збірки...")
            legacyMarker.delete()
            File(paths.cacheDir, "qemu_installed.marker").delete()
            markerFile.delete()
        }

        if (markerFile.exists() && guestBinaryInstaller.isInstalled(guestBinaries)) {
            onLog("QEMU вже встановлено.")
            return verifyInstallation(onLog)
        }

        onLog("Завантаження QEMU (Termux, Android/bionic)...")
        guestBinaryInstaller.installExecutables(
            packages = listOf(
                "qemu-utils",
                "qemu-system-aarch64-headless",
                "qemu-system-x86-64-headless",
            ),
            executables = listOf(
                TermuxExecutableSpec(
                    searchNames = listOf(QEMU_SYSTEM_AARCH64, "qemu-system-aarch64-headless"),
                    guestName = QEMU_SYSTEM_AARCH64,
                ),
                TermuxExecutableSpec(
                    searchNames = listOf(QEMU_SYSTEM_X86_64, "qemu-system-x86-64-headless", "qemu-system-x86-64"),
                    guestName = QEMU_SYSTEM_X86_64,
                ),
                TermuxExecutableSpec(
                    searchNames = listOf(QEMU_IMG),
                    guestName = QEMU_IMG,
                ),
            ),
            onLog = onLog,
        )

        onLog("Встановлення UEFI firmware (Alpine apk)...")
        var result = prootExecutor.execInRootfs(
            """
            apk update
            apk add --no-cache edk2-aarch64
            """.trimIndent(),
        )
        if (!result.success) return result

        markerFile.writeText("ok")
        return verifyInstallation(onLog)
    }

    suspend fun verifyInstallation(onLog: (String) -> Unit): ShellExecutor.Result {
        val result = prootExecutor.execInRootfs(
            """
            ${GuestShell.termuxBinary(paths, QEMU_SYSTEM_AARCH64, "--version")}
            ${GuestShell.termuxBinary(paths, QEMU_SYSTEM_X86_64, "--version")}
            ls -lh /usr/share/edk2-aarch64/QEMU_EFI.fd
            """.trimIndent(),
        )
        onLog(result.combinedOutput())
        return result
    }

    suspend fun createInstallDiskIfNeeded(onLog: (String) -> Unit): ShellExecutor.Result {
        if (paths.windowsDisk.exists()) {
            onLog("Диск для встановлення вже існує: ${paths.windowsDisk.name}\n")
            return ShellExecutor.Result(0, "", "", "skip")
        }

        onLog("Створення віртуального диска 48 GB для Windows...\n")
        return prootExecutor.execInRootfs(
            GuestShell.termuxBinary(
                paths,
                QEMU_IMG,
                "create -f qcow2 /images/${paths.windowsDisk.name} 48G",
            ),
        )
    }

    suspend fun launchWindows(
        config: WindowsImageConfig,
        onLine: (String) -> Unit,
    ): ShellExecutor.Result {
        require(paths.hasBootableImage()) {
            "Образ Windows не знайдено в ${paths.imagesDir.absolutePath}"
        }

        if (config.bootMode == WindowsBootMode.ISO) {
            val diskResult = createInstallDiskIfNeeded { line -> onLine("$line\n") }
            if (diskResult.exitCode != 0 && diskResult.command != "skip") {
                return diskResult
            }
        }

        val command = when (config.arch) {
            WindowsImageArch.ARM64, WindowsImageArch.AUTO -> buildArm64Command(config)
            WindowsImageArch.X86_64 -> buildX86Command(config)
        }

        onLine(
            when (config.arch) {
                WindowsImageArch.ARM64, WindowsImageArch.AUTO ->
                    ">>> Запуск Windows 11 ARM64 через QEMU (оптимально для вашого ARM-процесора)"
                WindowsImageArch.X86_64 ->
                    ">>> Запуск Windows 11 x86_64 через QEMU (повільна емуляція на ARM)"
            },
        )
        return prootExecutor.execStreamingInRootfs(command, onLine = onLine)
    }

    private fun buildArm64Command(config: WindowsImageConfig): String {
        return GuestShell.termuxBinary(
            paths,
            QEMU_SYSTEM_AARCH64,
            buildArm64Arguments(config),
        )
    }

    private fun buildArm64Arguments(config: WindowsImageConfig): String {
        val commonTail = """
            -machine virt,gic-version=3 \
            -cpu max \
            -smp 4 \
            -m 4096 \
            -drive if=pflash,format=raw,readonly=on,file=/usr/share/edk2-aarch64/QEMU_EFI.fd \
            -netdev user,id=net0 \
            -device virtio-net-device,netdev=net0 \
            -display none \
            -serial mon:stdio \
            -no-reboot
        """.trimIndent()

        return if (config.bootMode == WindowsBootMode.ISO) {
            """
            -drive if=none,file=/images/${paths.windowsIso.name},format=raw,media=cdrom,id=winiso \
            -device virtio-scsi-device,id=scsi0 \
            -device scsi-cd,bus=scsi0.0,drive=winiso,bootindex=0 \
            -drive if=none,file=/images/${paths.windowsDisk.name},format=qcow2,id=windisk \
            -device scsi-hd,bus=scsi0.0,drive=windisk,bootindex=1 \
            $commonTail
            """.trimIndent()
        } else {
            val disk = config.diskFileName ?: paths.windowsImage.name
            """
            -drive if=none,file=/images/$disk,format=qcow2,id=windisk \
            -device virtio-blk-device,drive=windisk,bootindex=1 \
            $commonTail
            """.trimIndent()
        }
    }

    private fun buildX86Command(config: WindowsImageConfig): String {
        val disk = when (config.bootMode) {
            WindowsBootMode.ISO -> paths.windowsIso.name
            WindowsBootMode.QCOW2 -> config.diskFileName ?: paths.windowsImage.name
        }
        val driveFormat = if (config.bootMode == WindowsBootMode.ISO) "raw" else "qcow2"
        val bootDevice = if (config.bootMode == WindowsBootMode.ISO) "-boot d" else ""

        return GuestShell.termuxBinary(
            paths,
            QEMU_SYSTEM_X86_64,
            """
            -machine q35 \
            -cpu qemu64 \
            -smp 2 \
            -m 2048 \
            -drive file=/images/$disk,if=virtio,format=$driveFormat \
            $bootDevice \
            -netdev user,id=net0 \
            -device virtio-net-pci,netdev=net0 \
            -display none \
            -serial mon:stdio \
            -no-reboot
            """.trimIndent(),
        )
    }
}
