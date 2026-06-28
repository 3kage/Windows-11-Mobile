package com.w11mobile.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File

/**
 * Executes shell commands in user-space via [ProcessBuilder].
 * Intended for PRoot/Termux-style environments where no root access is required.
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
        if (mergedEnvironment.isNotEmpty()) {
            processBuilder.environment().putAll(mergedEnvironment)
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
