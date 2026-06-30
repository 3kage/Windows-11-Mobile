package com.w11mobile.core.environment

import android.app.Application
import android.net.Uri
import com.w11mobile.core.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class EnvironmentSetupOrchestrator(
    application: Application,
    private val preferences: SetupPreferences,
    private val onStepChanged: (SetupStep) -> Unit,
    private val onProgressChanged: (Int, Boolean) -> Unit,
    private val onLog: (String) -> Unit,
) {
    private val paths = AppPaths(
        application.filesDir,
        application.cacheDir,
        application.codeCacheDir,
        application.applicationInfo.nativeLibraryDir,
    )
    private val downloadManager = DownloadManager()
    private val shellExecutor = ShellExecutor(
        workingDirectory = application.filesDir,
        environment = mapOf(
            "PATH" to "/system/bin:/system/xbin:/vendor/bin",
            "HOME" to application.filesDir.absolutePath,
            "TERM" to "xterm-256color",
        ),
    )
    private val prootInstaller = TermuxProotInstaller(paths, downloadManager)
    private val rootfsManager = RootfsManager(paths, downloadManager)
    private val prootExecutor = PRootExecutor(paths, prootInstaller, shellExecutor)
    private val guestBinaryInstaller = TermuxGuestBinaryInstaller(paths, downloadManager)
    private val qemuManager = QemuManager(application, paths, shellExecutor, guestBinaryInstaller, preferences)
    private val windowsImageManager = WindowsImageManager(paths, downloadManager)
    private val localImageImporter = LocalImageImporter(application, paths)

    suspend fun runFullSetup(
        imageSource: ImageSource,
        windowsImageUrl: String,
        localImageUri: String?,
        localImageName: String?,
        imageArch: WindowsImageArch,
    ) = withContext(Dispatchers.IO) {
        preferences.imageSource = imageSource
        preferences.windowsImageUrl = windowsImageUrl
        preferences.localImageUri = localImageUri
        preferences.localImageName = localImageName
        preferences.windowsImageArch = imageArch

        try {
            migrateLegacyPersistedAssetsIfNeeded()

            runStep(SetupStep.VERIFY_DEVICE) { verifyDevice() }

            if (EnvironmentReadiness.isPersistedEnvironmentReady(preferences, paths)) {
                skipDeployedAssetsSteps()
            } else {
                runStep(SetupStep.INSTALL_PROOT) { installProot() }
                runStep(SetupStep.INSTALL_ROOTFS) { installRootfs() }
                runStep(SetupStep.CONFIGURE_ROOTFS) { configureRootfs() }
            }

            if (qemuManager.isReady()) {
                skipQemuInstallStep()
            } else {
                runStep(SetupStep.INSTALL_QEMU) { installQemu() }
            }

            runStep(SetupStep.DOWNLOAD_WINDOWS_IMAGE) {
                prepareWindowsImage(
                    imageSource = imageSource,
                    url = windowsImageUrl,
                    localImageUri = localImageUri,
                    localImageName = localImageName,
                    imageArch = imageArch,
                )
            }
            runStep(SetupStep.VERIFY_ENVIRONMENT) { verifyEnvironment() }

            preferences.lastAssetsVersion = EnvironmentAssets.ASSETS_VERSION
            onStepChanged(SetupStep.COMPLETE)
            onProgressChanged(100, false)
            preferences.setupComplete = true
            onLog("\n>>> Середовище Windows 11 готове до запуску.\n")
        } catch (error: Exception) {
            onStepChanged(SetupStep.ERROR)
            onLog("\n[ПОМИЛКА] ${error.message}\n")
            throw error
        }
    }

    suspend fun launchWindows() = withContext(Dispatchers.IO) {
        require(canLaunchWindows()) {
            "Спочатку завершіть ініціалізацію та завантажте образ Windows."
        }
        qemuManager.launchWindows(
            config = paths.readImageConfig() ?: defaultImageConfig(),
            onLine = { line -> onLog("$line\n") },
        )
    }

    private fun defaultImageConfig(): WindowsImageConfig {
        return when {
            paths.windowsIso.exists() -> WindowsImageConfig(
                arch = preferences.windowsImageArch.let {
                    if (it == WindowsImageArch.AUTO) WindowsImageArch.ARM64 else it
                },
                bootMode = WindowsBootMode.ISO,
                source = "local",
                isoFileName = paths.windowsIso.name,
                diskFileName = paths.windowsDisk.name,
            )

            else -> WindowsImageConfig(
                arch = preferences.windowsImageArch.let {
                    if (it == WindowsImageArch.AUTO) WindowsImageArch.X86_64 else it
                },
                bootMode = WindowsBootMode.QCOW2,
                source = "local",
                diskFileName = paths.windowsImage.name,
            )
        }
    }

    fun isEnvironmentReady(): Boolean =
        EnvironmentReadiness.isPersistedEnvironmentReady(preferences, paths) &&
            paths.prootNativeLib.exists() &&
            paths.prootNativeLib.length() > 0L &&
            prootInstaller.isProotReady() &&
            qemuManager.isReady()

    fun canLaunchWindows(): Boolean =
        isEnvironmentReady() && paths.hasBootableImage()

    private suspend fun runStep(step: SetupStep, block: suspend () -> Unit) {
        onStepChanged(step)
        onProgressChanged(SetupStep.progressBefore(step), false)
        onLog("\n=== ${step.labelUk} ===\n")
        block()
        onProgressChanged(
            (SetupStep.progressBefore(step) + step.weight).coerceAtMost(100),
            false,
        )
    }

    private suspend fun verifyDevice() {
        logCommand("uname", "-a")
        logCommand("id")
    }

    private fun migrateLegacyPersistedAssetsIfNeeded() {
        if (EnvironmentReadiness.isAssetsVersionCurrent(preferences)) {
            return
        }
        if (!EnvironmentReadiness.hasRootfs(paths) ||
            !EnvironmentReadiness.hasTermuxLibraries(paths) ||
            !preferences.setupComplete
        ) {
            return
        }

        onLog(
            "\n>>> Знайдено розгорнуте середовище з попередньої версії APK — " +
                "прив'язуємо до assets v${EnvironmentAssets.ASSETS_VERSION} без перекопіювання.\n",
        )
        preferences.lastAssetsVersion = EnvironmentAssets.ASSETS_VERSION
    }

    private fun skipDeployedAssetsSteps() {
        onLog(
            "\n>>> Середовище вже розгорнуто (assets v${EnvironmentAssets.ASSETS_VERSION}) — " +
                "пропускаємо PRoot/rootfs/Termux libs.\n",
        )
        val progressAfterConfigure = SetupStep.progressBefore(SetupStep.INSTALL_QEMU)
        onStepChanged(SetupStep.CONFIGURE_ROOTFS)
        onProgressChanged(progressAfterConfigure, false)
    }

    private fun skipQemuInstallStep() {
        onLog("\n=== ${SetupStep.INSTALL_QEMU.labelUk} ===\nQEMU вже встановлено.\n")
        val progressAfterQemu =
            SetupStep.progressBefore(SetupStep.INSTALL_QEMU) + SetupStep.INSTALL_QEMU.weight
        onStepChanged(SetupStep.INSTALL_QEMU)
        onProgressChanged(progressAfterQemu.coerceAtMost(100), false)
    }

    private suspend fun installProot() {
        prootInstaller.install { downloaded, total ->
            reportDownloadProgress(SetupStep.INSTALL_PROOT, downloaded, total)
        }
        onLog("PRoot: ${paths.prootNativeLib.absolutePath}\n")
        onLog("Loader: ${prootInstaller.findProotLoader()?.absolutePath}\n")
        onLog("Libs: ${paths.libDir.absolutePath}\n")
    }

    private suspend fun installRootfs() {
        rootfsManager.installAlpineRootfs { downloaded, total ->
            reportDownloadProgress(SetupStep.INSTALL_ROOTFS, downloaded, total)
        }
        onLog("Rootfs: ${paths.rootfsDir.absolutePath}\n")
    }

    private suspend fun configureRootfs() {
        rootfsManager.configureRootfs()
        val result = prootExecutor.execInRootfs(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            echo "Termux-подібне середовище активне"
            cat /etc/alpine-release
            uname -a
            """.trimIndent(),
        )
        logResult(result)
        require(result.success) { "Не вдалося увійти в PRoot rootfs" }
    }

    private suspend fun installQemu() {
        val result = qemuManager.install(onLog = { line -> onLog("$line\n") })
        require(result.success) { "Не вдалося встановити QEMU: ${result.stderr}" }
    }

    suspend fun importLocalImageOnly(
        localImageUri: String,
        localImageName: String?,
        imageArch: WindowsImageArch,
    ) = withContext(Dispatchers.IO) {
        preferences.imageSource = ImageSource.LOCAL
        preferences.localImageUri = localImageUri
        preferences.localImageName = localImageName
        preferences.windowsImageArch = imageArch
        onLog("\n=== Імпорт локального образу ===\n")
        importLocalWindowsImage(localImageUri, localImageName, imageArch)
        updateLaunchReadiness()
    }

    private fun updateLaunchReadiness() {
        if (canLaunchWindows()) {
            onLog("\n>>> Образ Windows готовий до запуску.\n")
        }
    }

    private suspend fun prepareWindowsImage(
        imageSource: ImageSource,
        url: String,
        localImageUri: String?,
        localImageName: String?,
        imageArch: WindowsImageArch,
    ) {
        when (imageSource) {
            ImageSource.URL -> downloadWindowsImage(url, imageArch)
            ImageSource.LOCAL -> importLocalWindowsImage(localImageUri, localImageName, imageArch)
        }
    }

    private suspend fun importLocalWindowsImage(
        uriString: String?,
        displayName: String?,
        imageArch: WindowsImageArch,
    ) {
        require(!uriString.isNullOrBlank()) {
            "Оберіть локальний файл образу (.qcow2 або .iso) на пристрої."
        }

        if (windowsImageManager.isDownloadedForLocal(uriString)) {
            onLog("Локальний образ вже імпортовано.\n")
            logImageSummary()
            return
        }

        onLog("Імпорт локального файлу: ${displayName ?: uriString}\n")
        val uri = Uri.parse(uriString)
        val imported = localImageImporter.importFromUri(uri, displayName) { copied, total ->
            reportDownloadProgress(SetupStep.DOWNLOAD_WINDOWS_IMAGE, copied, total)
        }

        finalizeWindowsImage(
            sourceLabel = "local:$uriString",
            importedFile = imported,
            isIso = imported.name.endsWith(".iso", ignoreCase = true),
            imageArch = imageArch,
            displayName = displayName ?: imported.name,
        )
    }

    private suspend fun downloadWindowsImage(url: String, imageArch: WindowsImageArch) {
        if (url.isBlank()) {
            onLog("URL образу Windows не вказано — пропускаємо завантаження.\n")
            onLog("Оберіть локальний файл або вкажіть URL і повторіть ініціалізацію.\n")
            return
        }

        if (windowsImageManager.isDownloadedForUrl(url)) {
            onLog("Образ Windows вже завантажено.\n")
            logImageSummary()
            return
        }

        val downloaded = File(
            paths.cacheDir,
            if (url.endsWith(".iso", ignoreCase = true)) "windows.iso" else "windows.qcow2",
        )
        downloadManager.download(url, downloaded) { downloadedBytes, totalBytes ->
            reportDownloadProgress(SetupStep.DOWNLOAD_WINDOWS_IMAGE, downloadedBytes, totalBytes)
        }

        finalizeWindowsImage(
            sourceLabel = url,
            importedFile = downloaded,
            isIso = url.endsWith(".iso", ignoreCase = true),
            imageArch = imageArch,
            displayName = url.substringAfterLast('/'),
        )
        preferences.windowsImageUrl = url
    }

    private suspend fun finalizeWindowsImage(
        sourceLabel: String,
        importedFile: File,
        isIso: Boolean,
        imageArch: WindowsImageArch,
        displayName: String,
    ) {
        val detectedArch = ImageArchDetector.detect(displayName, imageArch)
        onLog("Архітектура образу: ${detectedArch.name}\n")

        if (isIso && detectedArch == WindowsImageArch.ARM64) {
            onLog("Режим ARM64 ISO — завантаження інсталятора без конвертації.\n")
            if (paths.windowsIso.exists()) paths.windowsIso.delete()
            require(importedFile.renameTo(paths.windowsIso)) { "Не вдалося зберегти ISO" }

            val diskResult = qemuManager.createInstallDiskIfNeeded { line -> onLog(line) }
            if (diskResult.exitCode != 0 && diskResult.command != "skip") {
                logResult(diskResult)
                error("Не вдалося створити диск для встановлення Windows")
            }

            WindowsImageConfigStore.write(
                paths.windowsImageMeta,
                WindowsImageConfig(
                    arch = detectedArch,
                    bootMode = WindowsBootMode.ISO,
                    source = sourceLabel,
                    isoFileName = paths.windowsIso.name,
                    diskFileName = paths.windowsDisk.name,
                ),
                paths.windowsIso.length(),
            )
        } else if (isIso) {
            onLog("Конвертація x86 ISO → QCOW2 через libqemu_img.so...\n")
            val isoFile = File(paths.imagesDir, "windows.iso")
            if (isoFile.exists()) isoFile.delete()
            require(importedFile.renameTo(isoFile)) { "Не вдалося перемістити ISO" }
            val result = shellExecutor.executeWithArgs(
                args = QemuNativeLauncher.buildInvocation(
                    paths.qemuImgNativeLib,
                    listOf(
                        "convert",
                        "-O",
                        "qcow2",
                        isoFile.absolutePath,
                        paths.windowsImage.absolutePath,
                    ),
                ),
                environment = QemuNativeLauncher.buildEnvironment(paths),
            )
            logResult(result)
            require(result.success) { "Не вдалося конвертувати ISO в QCOW2" }
            isoFile.delete()
            WindowsImageConfigStore.write(
                paths.windowsImageMeta,
                WindowsImageConfig(
                    arch = WindowsImageArch.X86_64,
                    bootMode = WindowsBootMode.QCOW2,
                    source = sourceLabel,
                    diskFileName = paths.windowsImage.name,
                ),
                paths.windowsImage.length(),
            )
        } else {
            if (paths.windowsImage.exists()) paths.windowsImage.delete()
            require(importedFile.renameTo(paths.windowsImage)) { "Не вдалося зберегти QCOW2 образ" }
            WindowsImageConfigStore.write(
                paths.windowsImageMeta,
                WindowsImageConfig(
                    arch = detectedArch,
                    bootMode = WindowsBootMode.QCOW2,
                    source = sourceLabel,
                    diskFileName = paths.windowsImage.name,
                ),
                paths.windowsImage.length(),
            )
        }

        logImageSummary()
    }

    private fun logImageSummary() {
        onLog("ISO: ${if (paths.windowsIso.exists()) paths.windowsIso.absolutePath else "—"}\n")
        onLog("Диск: ${if (paths.windowsDisk.exists()) paths.windowsDisk.absolutePath else "—"}\n")
        onLog("QCOW2: ${if (paths.windowsImage.exists()) paths.windowsImage.absolutePath else "—"}\n")
        paths.readImageConfig()?.let { config ->
            onLog("Конфіг: ${config.arch.name}, boot=${config.bootMode.name.lowercase()}\n")
        }
    }

    private suspend fun verifyEnvironment() {
        val result = qemuManager.verifyInstallation(onLog = { line -> onLog("$line\n") })
        logResult(result)
        require(result.success) { "Фінальна перевірка середовища не пройшла" }
    }

    private suspend fun logCommand(command: String, vararg args: String) {
        onLog("$ ${listOf(command, *args).joinToString(" ")}\n")
        val result = shellExecutor.execute(command, *args)
        logResult(result)
    }

    private fun logResult(result: ShellExecutor.Result) {
        onLog(result.combinedOutput())
        onLog("\n[exit ${result.exitCode}]\n")
        if (!result.success) {
            error("Команда завершилась з кодом ${result.exitCode}")
        }
    }

    private fun reportDownloadProgress(step: SetupStep, downloaded: Long, total: Long) {
        if (total <= 0L) {
            onProgressChanged(SetupStep.progressBefore(step), true)
            return
        }
        val stepProgress = ((downloaded.toDouble() / total.toDouble()) * step.weight).toInt()
        onProgressChanged((SetupStep.progressBefore(step) + stepProgress).coerceAtMost(100), false)
    }
}
