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
        val proot = paths.proot.absolutePath
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("proot-loader не знайдено")

        val args = buildList {
            add("/system/bin/linker64")
            add(proot)
            addAll(buildBindArgs(extraBinds))
            add("--link2symlink")
            add("-0")
            add("-R")
            add(paths.rootfsDir.absolutePath)
            add("/bin/sh")
            add("-c")
            add(command)
        }

        return shellExecutor.executeWithArgs(args, environment = buildEnvironment(loader))
    }

    suspend fun execStreamingInRootfs(
        command: String,
        extraBinds: List<String> = emptyList(),
        onLine: (String) -> Unit,
    ): ShellExecutor.Result {
        val proot = paths.proot.absolutePath
        val loader = prootInstaller.findProotLoader()?.absolutePath
            ?: error("proot-loader не знайдено")

        val args = buildList {
            add("/system/bin/linker64")
            add(proot)
            addAll(buildBindArgs(extraBinds))
            add("--link2symlink")
            add("-0")
            add("-R")
            add(paths.rootfsDir.absolutePath)
            add("/bin/sh")
            add("-c")
            add(command)
        }

        return shellExecutor.executeStreamingWithArgs(
            args = args,
            environment = buildEnvironment(loader),
            onLine = onLine,
        )
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
            add("${paths.baseDir.absolutePath}/images:/images")
            addAll(extraBinds)
        }
        return binds
    }

    private fun buildEnvironment(loaderPath: String): Map<String, String> = mapOf(
        "PROOT_LOADER" to loaderPath,
        "PROOT_NO_SECCOMP" to "1",
        "PATH" to "/system/bin:/system/xbin:/vendor/bin:${paths.binDir.absolutePath}",
        "HOME" to paths.baseDir.absolutePath,
        "TMPDIR" to paths.cacheDir.absolutePath,
    )
}
