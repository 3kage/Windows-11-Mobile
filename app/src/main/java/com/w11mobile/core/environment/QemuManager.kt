package com.w11mobile.core.environment

import android.content.Context
import com.w11mobile.core.ShellExecutor
import java.io.File

class QemuManager(
    private val context: Context,
    private val paths: AppPaths,
    private val shellExecutor: ShellExecutor,
    private val guestBinaryInstaller: TermuxGuestBinaryInstaller,
) {
    companion object {
        const val QEMU_ASSET_PATH = "firmware/QEMU_EFI.fd"
        private const val MARKER_NAME = "qemu_native_installed.marker"
    }

    private val markerFile = File(paths.cacheDir, MARKER_NAME)

    suspend fun install(onLog: (String) -> Unit): ShellExecutor.Result {
        clearLegacyMarkers()

        require(paths.qemuNativeLib.exists() && paths.qemuNativeLib.length() > 0L) {
            "libqemu.so не знайдено в ${paths.qemuNativeLib.absolutePath}. Перевстановіть APK."
        }
        onLog("QEMU: ${paths.qemuNativeLib.absolutePath}\n")

        if (!markerFile.exists() || !paths.libDir.list().orEmpty().any { it.endsWith(".so") }) {
            onLog("Завантаження залежностей QEMU (Termux Bionic libs)...\n")
            guestBinaryInstaller.installPackageLibraries(
                packages = listOf(
                    "qemu-utils",
                    "qemu-system-aarch64-headless",
                ),
                onLog = onLog,
            )
        } else {
            onLog("Залежності QEMU вже завантажено.\n")
        }

        onLog("Копіювання UEFI firmware (без Alpine apk)...\n")
        ensureUefiFirmware(onLog)
        ensureQemuRomFiles(onLog)

        markerFile.writeText("ok")
        return verifyInstallation(onLog)
    }

    suspend fun verifyInstallation(onLog: (String) -> Unit): ShellExecutor.Result {
        require(paths.uefiFirmware.exists() && paths.uefiFirmware.length() > 0L) {
            "UEFI firmware не знайдено: ${paths.uefiFirmware.absolutePath}"
        }
        ensureQemuRomFiles()

        val result = shellExecutor.executeWithArgs(
            args = QemuNativeLauncher.buildInvocation(
                paths.qemuNativeLib,
                listOf("--version"),
            ),
            environment = QemuNativeLauncher.buildEnvironment(paths),
        )
        onLog(buildString {
            append(result.combinedOutput())
            append("\nUEFI: ${paths.uefiFirmware.absolutePath} (${paths.uefiFirmware.length()} bytes)\n")
            append("QEMU ROM dir: ${paths.qemuShareDir.absolutePath}\n")
            append("efi-virtio.rom: ${paths.qemuVirtioRom.exists()}\n")
            append("ISO dir: ${paths.imagesDir.absolutePath}\n")
        })
        return result
    }

    suspend fun createInstallDiskIfNeeded(onLog: (String) -> Unit): ShellExecutor.Result {
        if (paths.windowsDisk.exists()) {
            onLog("Диск для встановлення вже існує: ${paths.windowsDisk.name}\n")
            return ShellExecutor.Result(0, "", "", "skip")
        }

        require(paths.qemuImgNativeLib.exists() && paths.qemuImgNativeLib.length() > 0L) {
            "libqemu_img.so не знайдено в ${paths.qemuImgNativeLib.absolutePath}"
        }

        onLog("Створення віртуального диска 48 GB для Windows...\n")
        return shellExecutor.executeWithArgs(
            args = QemuNativeLauncher.buildInvocation(
                paths.qemuImgNativeLib,
                listOf(
                    "create",
                    "-f",
                    "qcow2",
                    paths.windowsDisk.absolutePath,
                    "48G",
                ),
            ),
            environment = QemuNativeLauncher.buildEnvironment(paths),
        )
    }

    suspend fun launchWindows(
        config: WindowsImageConfig,
        onLine: (String) -> Unit,
    ): ShellExecutor.Result {
        require(paths.hasBootableImage()) {
            "Образ Windows не знайдено в ${paths.imagesDir.absolutePath}"
        }
        require(paths.qemuNativeLib.exists() && paths.qemuNativeLib.length() > 0L) {
            "libqemu.so не готовий. Завершіть крок встановлення QEMU."
        }
        ensureUefiFirmware(onLine)
        ensureQemuRomFiles()

        if (config.bootMode == WindowsBootMode.ISO) {
            val diskResult = createInstallDiskIfNeeded { line -> onLine("$line\n") }
            if (diskResult.exitCode != 0 && diskResult.command != "skip") {
                return diskResult
            }
        }

        val args = when (config.arch) {
            WindowsImageArch.ARM64, WindowsImageArch.AUTO -> buildArm64Arguments(config)
            WindowsImageArch.X86_64 -> error(
                "Прямий запуск x86_64 QEMU ще не підтримується без PRoot. Оберіть ARM64 образ.",
            )
        }

        onLine(">>> Запуск Windows 11 ARM64 через libqemu.so (прямий ProcessBuilder)\n")
        onLine("$ ${QemuNativeLauncher.buildInvocation(paths.qemuNativeLib, args).joinToString(" ")}\n")

        return shellExecutor.executeStreamingWithArgs(
            args = QemuNativeLauncher.buildInvocation(paths.qemuNativeLib, args),
            environment = QemuNativeLauncher.buildEnvironment(paths),
            onLine = onLine,
        )
    }

    private fun buildArm64Arguments(config: WindowsImageConfig): List<String> = when (config.bootMode) {
        WindowsBootMode.ISO -> {
            require(paths.windowsIso.exists()) {
                "ISO не знайдено: ${paths.windowsIso.absolutePath}"
            }
            QemuNativeLauncher.buildArm64IsoArguments(
                uefiFirmware = paths.uefiFirmware,
                isoFile = paths.windowsIso,
                qemuShareDir = paths.qemuShareDir,
                installDisk = paths.windowsDisk.takeIf { it.exists() },
            )
        }

        WindowsBootMode.QCOW2 -> {
            val disk = File(paths.imagesDir, config.diskFileName ?: paths.windowsImage.name)
            require(disk.exists()) { "QCOW2 не знайдено: ${disk.absolutePath}" }
            QemuNativeLauncher.buildArm64Qcow2Arguments(
                uefiFirmware = paths.uefiFirmware,
                diskFile = disk,
                qemuShareDir = paths.qemuShareDir,
            )
        }
    }

    private fun ensureQemuRomFiles(onLog: ((String) -> Unit)? = null) {
        require(paths.qemuVirtioRom.exists() && paths.qemuVirtioRom.length() > 0L) {
            buildString {
                append("QEMU ROM efi-virtio.rom не знайдено в ${paths.qemuShareDir.absolutePath}. ")
                append("Повторіть крок встановлення QEMU.")
                append("\nЗміст каталогу: ${paths.qemuShareDir.list()?.joinToString() ?: "(порожньо)"}")
            }
        }
        onLog?.invoke("QEMU ROM: ${paths.qemuVirtioRom.absolutePath}\n")
    }

    private fun ensureUefiFirmware(onLog: (String) -> Unit) {
        paths.uefiFirmwareDir.mkdirs()
        if (paths.uefiFirmware.exists() && paths.uefiFirmware.length() > 0L) {
            return
        }

        context.assets.open(QEMU_ASSET_PATH).use { input ->
            paths.uefiFirmware.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        onLog("UEFI firmware: ${paths.uefiFirmware.absolutePath}\n")
    }

    private fun clearLegacyMarkers() {
        listOf(
            "qemu_termux_installed.marker",
            "qemu_aarch64_installed.marker",
            "qemu_installed.marker",
        ).forEach { name ->
            File(paths.cacheDir, name).delete()
        }
    }

    fun isReady(): Boolean =
        paths.qemuNativeLib.exists() &&
            paths.qemuNativeLib.length() > 0L &&
            paths.uefiFirmware.exists() &&
            paths.uefiFirmware.length() > 0L &&
            paths.qemuVirtioRom.exists() &&
            paths.qemuVirtioRom.length() > 0L &&
            markerFile.exists()
}
