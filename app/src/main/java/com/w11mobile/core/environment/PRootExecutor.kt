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
            args = buildProotArgs(extraBinds, command),
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
            args = buildProotArgs(extraBinds, command),
            environment = buildEnvironment(),
            onLine = onLine,
        )
    }

    private fun prepareRuntime() {
        ProotRuntimePreparer.prepare(paths)
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("proot-loader не знайдено")
        require(paths.proot.canExecute()) { "proot не готовий до запуску" }
        require(File(loader).length() > 0L) { "proot-loader пошкоджений" }
    }

    private fun buildProotArgs(extraBinds: List<String>, command: String): List<String> =
        buildList {
            add("/system/bin/linker64")
            add(paths.proot.absolutePath)
            addAll(buildBindArgs(extraBinds))
            add("--link2symlink")
            add("-0")
            add("-r")
            add(paths.rootfsDir.absolutePath)
            add("-w")
            add("/root")
            add("/bin/sh")
            add("-c")
            add(command)
        }

    private fun buildBindArgs(extraBinds: List<String>): List<String> {
        val binds = buildList {
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
        return binds
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
        File(paths.rootfsDir, "root").mkdirs()
        File(paths.rootfsDir, "tmp").mkdirs()
        File(paths.rootfsDir, "images").mkdirs()
    }
}
