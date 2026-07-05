package com.w11mobile.core.environment

import android.content.Context
import com.w11mobile.BuildConfig
import com.w11mobile.core.ShellExecutor
import java.io.File

class QemuManager(
    private val context: Context,
    private val paths: AppPaths,
    private val shellExecutor: ShellExecutor,
    private val guestBinaryInstaller: TermuxGuestBinaryInstaller,
    private val preferences: SetupPreferences,
) {
    companion object {
        const val QEMU_UEFI_ASSET = "firmware/QEMU_EFI.fd"
        const val QEMU_VIRTIO_ROM_ASSET = "qemu/efi-virtio.rom"
        const val QEMU_KEYMAPS_ASSET_DIR = "qemu/keymaps"
    }

    fun ensureRuntimeAssets(onLog: ((String) -> Unit)? = null) {
        ensureUefiFirmware(onLog ?: {})
        ensureQemuRomFiles(onLog)
    }

    suspend fun install(onLog: (String) -> Unit): ShellExecutor.Result {
        clearLegacyMarkers()

        require(paths.qemuNativeLib.exists() && paths.qemuNativeLib.length() > 0L) {
            "libqemu.so не знайдено в ${paths.qemuNativeLib.absolutePath}. Перевстановіть APK."
        }
        onLog("QEMU: ${paths.qemuNativeLib.absolutePath}\n")
        onLog("Assets version: ${EnvironmentAssets.ASSETS_VERSION}\n")

        if (needsLibraryRefresh()) {
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

        return verifyInstallation(onLog)
    }

    suspend fun verifyInstallation(onLog: (String) -> Unit): ShellExecutor.Result {
        ensureUefiFirmware(onLog)
        ensureQemuRomFiles(onLog)

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
            append("QEMU ROM dir (-L): ${paths.qemuShareDir.absolutePath}\n")
            append("efi-virtio.rom: ${paths.qemuVirtioRom.length()} bytes\n")
            append("keymap en-us: ${paths.qemuEnUsKeymap.length()} bytes\n")
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

        onLine(">>> APK ${BuildConfig.VERSION_NAME} | assets v${EnvironmentAssets.ASSETS_VERSION}\n")
        ensureUefiFirmware(onLine)
        ensureQemuRomFiles(onLine)

        if (config.bootMode == WindowsBootMode.ISO) {
            val isoCheck = WindowsIsoValidator.validate(paths.windowsIso)
            onLine(">>> ${isoCheck.message}\n")
            require(isoCheck.ok) { isoCheck.message }

            WindowsIsoWarmup.warm(paths.windowsIso) { line -> onLine("$line\n") }

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

        QemuProcessSession.markLaunchStarting()

        val isoBootKeyInjector = if (config.bootMode == WindowsBootMode.ISO) {
            QemuIsoBootKeyInjector()
        } else {
            null
        }

        return try {
            shellExecutor.executeStreamingWithArgs(
                args = QemuNativeLauncher.buildInvocation(paths.qemuNativeLib, args),
                environment = QemuNativeLauncher.buildEnvironment(paths),
                onLine = { line ->
                    isoBootKeyInjector?.onOutputLine(line)
                    onLine(line)
                },
            )
        } finally {
            isoBootKeyInjector?.stop()
        }
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
        paths.qemuShareDir.mkdirs()
        ensureVirtioRom(onLog)
        ensureQemuKeymaps(onLog)

        onLog?.invoke("QEMU ROM (-L): ${paths.qemuShareDir.absolutePath}\n")
        onLog?.invoke("efi-virtio.rom: ${paths.qemuVirtioRom.length()} bytes\n")
        onLog?.invoke("keymap en-us: ${paths.qemuEnUsKeymap.length()} bytes\n")
    }

    private fun ensureVirtioRom(onLog: ((String) -> Unit)?) {
        if (!paths.qemuVirtioRom.exists() || paths.qemuVirtioRom.length() == 0L) {
            copyAsset(QEMU_VIRTIO_ROM_ASSET, paths.qemuVirtioRom)
        }

        val termuxRom = File(paths.termuxQemuShareDir, "efi-virtio.rom")
        if ((!paths.qemuVirtioRom.exists() || paths.qemuVirtioRom.length() == 0L) &&
            termuxRom.exists() && termuxRom.length() > 0L
        ) {
            termuxRom.copyTo(paths.qemuVirtioRom, overwrite = true)
            onLog?.invoke("QEMU ROM copied from Termux: ${termuxRom.absolutePath}\n")
        }

        require(paths.qemuVirtioRom.exists() && paths.qemuVirtioRom.length() > 0L) {
            buildString {
                append("QEMU ROM efi-virtio.rom не знайдено. ")
                append("Очікуваний шлях: ${paths.qemuVirtioRom.absolutePath}. ")
                append("Повторіть ініціалізацію або збільште ASSETS_VERSION після оновлення payload.")
                append("\nTermux ROM dir: ${paths.termuxQemuShareDir.list()?.joinToString() ?: "(порожньо)"}")
            }
        }
    }

    private fun ensureQemuKeymaps(onLog: ((String) -> Unit)?) {
        if (!paths.qemuEnUsKeymap.exists() || paths.qemuEnUsKeymap.length() == 0L) {
            copyAssetDirectory(QEMU_KEYMAPS_ASSET_DIR, paths.qemuKeymapsDir)
        }

        val termuxKeymapsDir = File(paths.termuxQemuShareDir, "keymaps")
        if ((!paths.qemuEnUsKeymap.exists() || paths.qemuEnUsKeymap.length() == 0L) &&
            termuxKeymapsDir.isDirectory
        ) {
            copyDirectory(termuxKeymapsDir, paths.qemuKeymapsDir)
            onLog?.invoke("QEMU keymaps copied from Termux: ${termuxKeymapsDir.absolutePath}\n")
        }

        require(paths.qemuEnUsKeymap.exists() && paths.qemuEnUsKeymap.length() > 0L) {
            buildString {
                append("QEMU keymap en-us не знайдено. ")
                append("Очікуваний шлях: ${paths.qemuEnUsKeymap.absolutePath}. ")
                append("Повторіть ініціалізацію або збільште ASSETS_VERSION після оновлення payload.")
                append("\nTermux keymaps dir: ${termuxKeymapsDir.list()?.joinToString() ?: "(порожньо)"}")
            }
        }
    }

    private fun ensureUefiFirmware(onLog: (String) -> Unit) {
        paths.uefiFirmwareDir.mkdirs()
        if (paths.uefiFirmware.exists() && paths.uefiFirmware.length() > 0L) {
            return
        }

        copyAsset(QEMU_UEFI_ASSET, paths.uefiFirmware)
        onLog("UEFI firmware: ${paths.uefiFirmware.absolutePath}\n")
    }

    private fun copyAsset(assetPath: String, target: File) {
        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun copyAssetDirectory(assetDir: String, targetDir: File) {
        val entries = context.assets.list(assetDir) ?: emptyArray()
        if (entries.isEmpty()) {
            copyAsset(assetDir, targetDir)
            return
        }

        targetDir.mkdirs()
        entries.forEach { entry ->
            copyAssetDirectory("$assetDir/$entry", File(targetDir, entry))
        }
    }

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        sourceDir.walkTopDown().forEach { source ->
            val relativePath = source.relativeTo(sourceDir).path
            val target = if (relativePath.isEmpty()) targetDir else File(targetDir, relativePath)
            if (source.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }

    private fun needsLibraryRefresh(): Boolean =
        !EnvironmentReadiness.hasTermuxLibraries(paths)

    private fun clearLegacyMarkers() {
        listOf(
            "qemu_native_installed.marker",
            "qemu_termux_installed.marker",
            "qemu_aarch64_installed.marker",
            "qemu_installed.marker",
        ).forEach { name ->
            File(paths.cacheDir, name).delete()
        }
    }

    fun isReady(): Boolean = EnvironmentReadiness.isQemuRuntimeReady(paths)
}
