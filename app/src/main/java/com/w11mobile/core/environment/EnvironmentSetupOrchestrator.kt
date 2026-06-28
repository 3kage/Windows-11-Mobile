package com.w11mobile.core.environment

import android.app.Application
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
    private val paths = AppPaths(application.filesDir)
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
    private val qemuManager = QemuManager(paths, prootExecutor)
    private val windowsImageManager = WindowsImageManager(paths, downloadManager)

    suspend fun runFullSetup(windowsImageUrl: String) = withContext(Dispatchers.IO) {
        preferences.windowsImageUrl = windowsImageUrl

        try {
            runStep(SetupStep.VERIFY_DEVICE) { verifyDevice() }
            runStep(SetupStep.INSTALL_PROOT) { installProot() }
            runStep(SetupStep.INSTALL_ROOTFS) { installRootfs() }
            runStep(SetupStep.CONFIGURE_ROOTFS) { configureRootfs() }
            runStep(SetupStep.INSTALL_QEMU) { installQemu() }
            runStep(SetupStep.DOWNLOAD_WINDOWS_IMAGE) { downloadWindowsImage(windowsImageUrl) }
            runStep(SetupStep.VERIFY_ENVIRONMENT) { verifyEnvironment() }

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
        qemuManager.launchWindows(onLine = { line -> onLog("$line\n") })
    }

    fun isEnvironmentReady(): Boolean =
        paths.proot.canExecute() &&
            File(paths.rootfsDir, "bin/sh").exists() &&
            File(paths.cacheDir, "qemu_installed.marker").exists()

    fun canLaunchWindows(): Boolean =
        isEnvironmentReady() && paths.windowsImage.exists()

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

    private suspend fun installProot() {
        prootInstaller.install { downloaded, total ->
            reportDownloadProgress(SetupStep.INSTALL_PROOT, downloaded, total)
        }
        onLog("PRoot: ${paths.proot.absolutePath}\n")
        onLog("Loader: ${prootInstaller.findProotLoader()?.absolutePath}\n")
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

    private suspend fun downloadWindowsImage(url: String) {
        if (url.isBlank()) {
            onLog("URL образу Windows не вказано — пропускаємо завантаження.\n")
            onLog("Введіть URL (.qcow2/.iso) і повторіть ініціалізацію.\n")
            return
        }

        if (windowsImageManager.isDownloadedForUrl(url)) {
            onLog("Образ Windows вже завантажено: ${paths.windowsImage.absolutePath}\n")
            return
        }

        val downloaded = File(
            paths.cacheDir,
            if (url.endsWith(".iso", ignoreCase = true)) "windows.iso" else "windows.qcow2",
        )
        downloadManager.download(url, downloaded) { downloadedBytes, totalBytes ->
            reportDownloadProgress(SetupStep.DOWNLOAD_WINDOWS_IMAGE, downloadedBytes, totalBytes)
        }

        if (url.endsWith(".iso", ignoreCase = true)) {
            onLog("Конвертація ISO → QCOW2 через qemu-img...\n")
            val isoFile = File(paths.imagesDir, "windows.iso")
            if (isoFile.exists()) isoFile.delete()
            require(downloaded.renameTo(isoFile)) { "Не вдалося перемістити ISO" }
            val result = prootExecutor.execInRootfs(
                """
                export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
                qemu-img convert -O qcow2 /images/windows.iso /images/windows.qcow2
                rm -f /images/windows.iso
                """.trimIndent(),
            )
            logResult(result)
            require(result.success) { "Не вдалося конвертувати ISO в QCOW2" }
        } else {
            if (paths.windowsImage.exists()) paths.windowsImage.delete()
            require(downloaded.renameTo(paths.windowsImage)) { "Не вдалося зберегти QCOW2 образ" }
        }

        paths.windowsImageMeta.writeText(
            buildString {
                appendLine("url=$url")
                appendLine("size=${paths.windowsImage.length()}")
            },
        )
        preferences.windowsImageUrl = url
        onLog("Образ Windows: ${paths.windowsImage.absolutePath} (${paths.windowsImage.length()} bytes)\n")
    }

    private suspend fun verifyEnvironment() {
        val result = prootExecutor.execInRootfs(
            """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            echo "=== Перевірка ==="
            proot-info() { echo "PRoot OK"; }
            which qemu-system-x86_64
            ls -lh /images || true
            """.trimIndent(),
        )
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
