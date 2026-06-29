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
        val busybox = prepareRuntime()
        return shellExecutor.executeWithArgs(
            args = buildProotInvocation(busybox, extraBinds, command),
            environment = buildEnvironment(),
        )
    }

    suspend fun execStreamingInRootfs(
        command: String,
        extraBinds: List<String> = emptyList(),
        onLine: (String) -> Unit,
    ): ShellExecutor.Result {
        val busybox = prepareRuntime()
        return shellExecutor.executeStreamingWithArgs(
            args = buildProotInvocation(busybox, extraBinds, command),
            environment = buildEnvironment(),
            onLine = onLine,
        )
    }

    private fun prepareRuntime(): File {
        ProotRuntimePreparer.prepare(paths)
        val busybox = GuestBusyboxStager.ensureReady(paths)
        require(paths.prootNativeLib.exists() && paths.prootNativeLib.length() > 0L) {
            "libproot.so не знайдено в ${paths.prootNativeLib.absolutePath}. Перевстановіть APK."
        }
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("libproot_loader.so не знайдено")
        require(File(loader).length() > 0L) { "libproot_loader.so пошкоджений" }
        return busybox
    }

    private fun buildProotInvocation(
        busybox: File,
        extraBinds: List<String>,
        command: String,
    ): List<String> {
        val prootArgs = buildList {
            addAll(buildBindArgs(busybox, extraBinds))
            add("--link2symlink")
            add("-0")
            add("-r")
            add(paths.rootfsDir.absolutePath)
            add("-w")
            add("/root")
            add("/system/bin/linker64")
            add("/exec/busybox")
            add("sh")
            add("-c")
            add(GuestShell.wrap(paths, command))
        }
        return ShellExecutor.buildNativeProotInvocation(paths.prootNativeLib, prootArgs)
    }

    private fun buildBindArgs(busybox: File, extraBinds: List<String>): List<String> {
        val busyboxPath = busybox.absolutePath
        return buildList {
            add("-b")
            add("/system:/system")
            add("-b")
            add("$busyboxPath:/exec/busybox")
            add("-b")
            add("$busyboxPath:/bin/busybox")
            add("-b")
            add("$busyboxPath:/bin/sh")
            add("-b")
            add("${paths.guestExecDir.absolutePath}:/exec/guest")
            add("-b")
            add("/dev")
            add("-b")
            add("/proc")
            add("-b")
            add("/sys")
            add("-b")
            add("${paths.imagesDir.absolutePath}:/images")
            addAll(extraBinds)
        }
    }

    private fun buildEnvironment(): Map<String, String> {
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("libproot_loader.so не знайдено")

        return ShellExecutor.buildProotEnvironment(
            appCacheDir = paths.appCacheDir,
            prootLoaderPath = loader,
            ldLibraryPath = paths.libDir.absolutePath,
        ) + mapOf(
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${paths.binDir.absolutePath}:/system/bin",
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
