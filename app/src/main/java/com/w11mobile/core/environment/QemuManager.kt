package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import java.io.File

class QemuManager(
    private val paths: AppPaths,
    private val prootExecutor: PRootExecutor,
) {
    private val markerFile = File(paths.cacheDir, "qemu_aarch64_installed.marker")

    suspend fun install(onLog: (String) -> Unit): ShellExecutor.Result {
        val legacyMarker = File(paths.cacheDir, "qemu_installed.marker")
        if (markerFile.exists()) {
            onLog("QEMU вже встановлено.")
            return verifyInstallation(onLog)
        }
        if (legacyMarker.exists()) {
            onLog("Оновлення QEMU для підтримки Windows 11 ARM64...")
            legacyMarker.delete()
        }

        onLog("Оновлення apk-репозиторіїв Alpine...")
        var result = prootExecutor.execInRootfs(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            apk update
            """.trimIndent(),
        )
        if (!result.success) return result

        onLog("Встановлення QEMU ARM64/x86_64 та UEFI firmware...")
        result = prootExecutor.execInRootfs(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            apk add --no-cache \
              qemu-system-aarch64 \
              qemu-system-x86_64 \
              qemu-img \
              edk2-aarch64
            """.trimIndent(),
        )
        if (!result.success) return result

        markerFile.writeText("ok")
        return verifyInstallation(onLog)
    }

    suspend fun verifyInstallation(onLog: (String) -> Unit): ShellExecutor.Result {
        val result = prootExecutor.execInRootfs(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            qemu-system-aarch64 --version
            qemu-system-x86_64 --version
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
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            qemu-img create -f qcow2 /images/${paths.windowsDisk.name} 48G
            """.trimIndent(),
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
        return if (config.bootMode == WindowsBootMode.ISO) {
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            qemu-system-aarch64 \
              -machine virt,gic-version=3 \
              -cpu max \
              -smp 4 \
              -m 4096 \
              -drive if=pflash,format=raw,readonly=on,file=/usr/share/edk2-aarch64/QEMU_EFI.fd \
              -drive if=none,file=/images/${paths.windowsIso.name},format=raw,media=cdrom,id=winiso \
              -device virtio-scsi-device,id=scsi0 \
              -device scsi-cd,bus=scsi0.0,drive=winiso,bootindex=0 \
              -drive if=none,file=/images/${paths.windowsDisk.name},format=qcow2,id=windisk \
              -device scsi-hd,bus=scsi0.0,drive=windisk,bootindex=1 \
              -netdev user,id=net0 \
              -device virtio-net-device,netdev=net0 \
              -display none \
              -serial mon:stdio \
              -no-reboot
            """.trimIndent()
        } else {
            val disk = config.diskFileName ?: paths.windowsImage.name
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            qemu-system-aarch64 \
              -machine virt,gic-version=3 \
              -cpu max \
              -smp 4 \
              -m 4096 \
              -drive if=pflash,format=raw,readonly=on,file=/usr/share/edk2-aarch64/QEMU_EFI.fd \
              -drive if=none,file=/images/$disk,format=qcow2,id=windisk \
              -device virtio-blk-device,drive=windisk,bootindex=1 \
              -netdev user,id=net0 \
              -device virtio-net-device,netdev=net0 \
              -display none \
              -serial mon:stdio \
              -no-reboot
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

        return """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            qemu-system-x86_64 \
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
        """.trimIndent()
    }
}
