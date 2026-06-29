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
            args = buildProotArgs(busybox, extraBinds, command),
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
            args = buildProotArgs(busybox, extraBinds, command),
            environment = buildEnvironment(),
            onLine = onLine,
        )
    }

    private fun prepareRuntime(): File {
        ProotRuntimePreparer.prepare(paths)
        val busybox = GuestBusyboxStager.ensureReady(paths)
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("proot-loader не знайдено")
        require(paths.proot.canExecute()) { "proot не готовий до запуску" }
        require(File(loader).length() > 0L) { "proot-loader пошкоджений" }
        return busybox
    }

    private fun buildProotArgs(
        busybox: File,
        extraBinds: List<String>,
        command: String,
    ): List<String> =
        buildList {
            add("/system/bin/linker64")
            add(paths.proot.absolutePath)
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
            ?: error("proot-loader не знайдено")

        return mapOf(
            "PROOT_LOADER" to loader,
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_TMP_DIR" to paths.prootTmpDir.absolutePath,
            "PROOT_F2FS_WORKAROUND" to "1",
            "LD_LIBRARY_PATH" to paths.libDir.absolutePath,
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
