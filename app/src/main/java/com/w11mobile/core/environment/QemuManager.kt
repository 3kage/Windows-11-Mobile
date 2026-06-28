package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import java.io.File

class QemuManager(
    private val paths: AppPaths,
    private val prootExecutor: PRootExecutor,
) {
    private val markerFile = File(paths.cacheDir, "qemu_installed.marker")

    suspend fun install(onLog: (String) -> Unit): ShellExecutor.Result {
        if (markerFile.exists()) {
            onLog("QEMU вже встановлено.")
            return verifyInstallation(onLog)
        }

        onLog("Оновлення apk-репозиторіїв Alpine...")
        var result = prootExecutor.execInRootfs(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            apk update
            """.trimIndent(),
        )
        if (!result.success) return result

        onLog("Встановлення qemu-system-x86_64 та qemu-img...")
        result = prootExecutor.execInRootfs(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            apk add --no-cache qemu-system-x86_64 qemu-img
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
            qemu-system-x86_64 --version
            qemu-img --version
            """.trimIndent(),
        )
        onLog(result.combinedOutput())
        return result
    }

    suspend fun launchWindows(
        onLine: (String) -> Unit,
    ): ShellExecutor.Result {
        require(paths.windowsImage.exists()) {
            "Образ Windows не знайдено: ${paths.windowsImage.absolutePath}"
        }

        val imagePath = "/images/${paths.windowsImage.name}"
        val command = """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            qemu-system-x86_64 \
              -machine q35 \
              -cpu qemu64 \
              -smp 2 \
              -m 2048 \
              -drive file=$imagePath,if=virtio,format=qcow2 \
              -netdev user,id=net0 \
              -device virtio-net-pci,netdev=net0 \
              -display none \
              -serial mon:stdio \
              -no-reboot
        """.trimIndent()

        onLine(">>> Запуск Windows 11 через QEMU (TCG, без KVM)...")
        onLine(">>> Це може працювати повільно на ARM-процесорі.")
        return prootExecutor.execStreamingInRootfs(command, onLine = onLine)
    }
}
