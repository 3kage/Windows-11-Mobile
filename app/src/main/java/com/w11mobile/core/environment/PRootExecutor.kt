package com.w11mobile.core.environment

import com.w11mobile.core.ShellExecutor
import java.io.File

class PRootExecutor(
    private val paths: AppPaths,
    private val prootInstaller: TermuxProotInstaller,
    private val shellExecutor: ShellExecutor,
) {
    suspend fun execInRootfs(
        command: String,
        extraBinds: List<String> = emptyList(),
    ): ShellExecutor.Result {
        prepareRuntime()
        return shellExecutor.executeWithArgs(
            args = buildProotInvocation(extraBinds, command),
            environment = buildEnvironment(),
        )
    }

    suspend fun execStreamingInRootfs(
        command: String,
        extraBinds: List<String> = emptyList(),
        onLine: (String) -> Unit,
    ): ShellExecutor.Result {
        prepareRuntime()
        return shellExecutor.executeStreamingWithArgs(
            args = buildProotInvocation(extraBinds, command),
            environment = buildEnvironment(),
            onLine = onLine,
        )
    }

    private fun prepareRuntime() {
        ProotRuntimePreparer.prepare(paths)
        GuestBusyboxStager.ensureReady(paths)
        require(paths.prootNativeLib.exists() && paths.prootNativeLib.length() > 0L) {
            "libproot.so не знайдено в ${paths.prootNativeLib.absolutePath}. Перевстановіть APK."
        }
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("libproot_loader.so не знайдено")
        require(File(loader).length() > 0L) { "libproot_loader.so пошкоджений" }
    }

    private fun buildProotInvocation(
        extraBinds: List<String>,
        command: String,
    ): List<String> {
        val busybox = GuestBusyboxStager.ensureReady(paths)
        val busyboxPath = busybox.absolutePath
        val guestBinds = buildList {
            add("-b")
            add("$busyboxPath:/exec/busybox")
            add("-b")
            add("$busyboxPath:/bin/busybox")
            add("-b")
            add("$busyboxPath:/bin/sh")
            add("-b")
            add("${paths.guestExecDir.absolutePath}:/exec/guest")
            add("-b")
            add("${paths.imagesDir.absolutePath}:/images")
            addAll(extraBinds)
        }

        val request = ShellExecutor.ProotLaunchRequest(
            prootNativeLib = paths.prootNativeLib,
            rootfsDir = paths.rootfsDir,
            guestCommand = GuestShell.wrap(paths, command),
            guestShell = ShellExecutor.GUEST_SHELL,
            extraBindFlags = guestBinds,
        )
        return ShellExecutor.buildNativeProotInvocation(request)
    }

    private fun buildEnvironment(): Map<String, String> {
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("libproot_loader.so не знайдено")

        return ShellExecutor.buildProotEnvironment(
            appCacheDir = paths.appCacheDir,
            prootLoaderPath = loader,
            ldLibraryPath = paths.libDir.absolutePath,
        ) + mapOf(
            "HOME" to "/root",
            "TMPDIR" to "/tmp",
        )
    }
}

object ProotRuntimePreparer {
    fun prepare(paths: AppPaths) {
        paths.prootTmpDir.mkdirs()
        paths.guestExecDir.mkdirs()
        File(paths.rootfsDir, "root").mkdirs()
        File(paths.rootfsDir, "tmp").mkdirs()
        File(paths.rootfsDir, "images").mkdirs()
    }
}
