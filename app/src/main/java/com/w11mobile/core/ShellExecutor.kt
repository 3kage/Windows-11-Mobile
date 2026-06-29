package com.w11mobile.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File

/**
 * Executes shell commands in user-space via [ProcessBuilder].
 * PRoot is launched as a native library ([libproot.so]) through [linker64], Winlator-style.
 */
class ShellExecutor(
    private val workingDirectory: File? = null,
    private val environment: Map<String, String> = emptyMap(),
) {

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val command: String,
    ) {
        val success: Boolean get() = exitCode == 0

        fun combinedOutput(): String = buildString {
            if (stdout.isNotBlank()) append(stdout.trimEnd())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(stderr.trimEnd())
            }
        }
    }

    data class ProotLaunchRequest(
        val prootNativeLib: File,
        val rootfsDir: File,
        val guestCommand: String,
        val guestShell: String = GUEST_SHELL,
        val extraBindFlags: List<String> = emptyList(),
    )

    companion object {
        private const val LINKER64 = "/system/bin/linker64"
        const val GUEST_SHELL = "/bin/sh"
        const val LINUX_PATH = "/usr/bin:/bin:/usr/sbin:/sbin:/system/bin"

        private val ANDROID_SYSTEM_BINDS = listOf(
            "-b", "/system",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
        )

        /**
         * Required PRoot environment for Android 10+ / W^X and seccomp compatibility.
         */
        fun buildProotEnvironment(
            appCacheDir: File,
            prootLoaderPath: String,
            ldLibraryPath: String,
        ): Map<String, String> {
            val tmpDir = File(appCacheDir, "proot-tmp").apply { mkdirs() }
            return mapOf(
                "PROOT_LOADER" to prootLoaderPath,
                "PROOT_NO_SECCOMP" to "1",
                "PROOT_TMPDIR" to tmpDir.absolutePath,
                "PROOT_TMP_DIR" to tmpDir.absolutePath,
                "PROOT_F2FS_WORKAROUND" to "1",
                "LD_LIBRARY_PATH" to ldLibraryPath,
                "PATH" to LINUX_PATH,
            )
        }

        /**
         * PRoot CLI arguments: system binds, guest rootfs, then [guestShell] as the final executable.
         */
        fun buildProotArguments(request: ProotLaunchRequest): List<String> = buildList {
            addAll(ANDROID_SYSTEM_BINDS)
            addAll(request.extraBindFlags)
            add("--link2symlink")
            add("-0")
            add("-r")
            add(request.rootfsDir.absolutePath)
            add("-w")
            add("/root")
            add(request.guestShell)
            add("-c")
            add(request.guestCommand)
        }

        /**
         * Full host argv: linker64 + libproot.so + [buildProotArguments].
         */
        fun buildNativeProotInvocation(request: ProotLaunchRequest): List<String> =
            buildList {
                add(LINKER64)
                add(request.prootNativeLib.absolutePath)
                addAll(buildProotArguments(request))
            }
    }

    suspend fun execute(
        command: String,
        vararg args: String,
    ): Result = withContext(Dispatchers.IO) {
        executeBlocking(command, *args)
    }

    suspend fun executeWithArgs(
        args: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): Result = withContext(Dispatchers.IO) {
        executeBlockingWithArgs(args, environment)
    }

    suspend fun executeStreamingWithArgs(
        args: List<String>,
        environment: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        executeStreamingBlockingWithArgs(args, environment, onLine)
    }

    suspend fun executeScript(
        script: String,
        shell: String = "/system/bin/sh",
    ): Result = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(shell, "-c", script)
        configureProcess(processBuilder)
        runProcess(processBuilder, script)
    }

    fun executeBlocking(
        command: String,
        vararg args: String,
    ): Result {
        val processBuilder = ProcessBuilder(buildList {
            add(command)
            addAll(args)
        })
        configureProcess(processBuilder)
        val displayCommand = (listOf(command) + args).joinToString(" ")
        return runProcess(processBuilder, displayCommand)
    }

    fun executeBlockingWithArgs(
        args: List<String>,
        extraEnvironment: Map<String, String> = emptyMap(),
    ): Result {
        val processBuilder = ProcessBuilder(args)
        configureProcess(processBuilder, extraEnvironment)
        return runProcess(processBuilder, args.joinToString(" "))
    }

    fun executeStreamingBlockingWithArgs(
        args: List<String>,
        extraEnvironment: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit,
    ): Result {
        val processBuilder = ProcessBuilder(args)
        configureProcess(processBuilder, extraEnvironment)
        val process = processBuilder.start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutThread = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                synchronized(stdout) { stdout.appendLine(line) }
                onLine(line)
            }
        }
        val stderrThread = Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(stderr) { stderr.appendLine(line) }
                onLine(line)
            }
        }
        stdoutThread.start()
        stderrThread.start()
        stdoutThread.join()
        stderrThread.join()

        val exitCode = process.waitFor()
        return Result(
            exitCode = exitCode,
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            command = args.joinToString(" "),
        )
    }

    private fun configureProcess(
        processBuilder: ProcessBuilder,
        extraEnvironment: Map<String, String> = emptyMap(),
    ) {
        workingDirectory?.let { processBuilder.directory(it) }
        val mergedEnvironment = environment + extraEnvironment
        val env = processBuilder.environment()
        if (mergedEnvironment.isNotEmpty()) {
            env.putAll(mergedEnvironment)
        }
        if (env["PATH"].isNullOrBlank()) {
            env["PATH"] = LINUX_PATH
        }
        processBuilder.redirectErrorStream(false)
    }

    private fun runProcess(
        processBuilder: ProcessBuilder,
        displayCommand: String,
    ): Result {
        val process = processBuilder.start()
        val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        val exitCode = process.waitFor()
        return Result(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            command = displayCommand,
        )
    }
}
