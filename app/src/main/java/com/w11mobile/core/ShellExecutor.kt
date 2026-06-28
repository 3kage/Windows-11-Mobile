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

    private fun configureProcess(processBuilder: ProcessBuilder) {
        workingDirectory?.let { processBuilder.directory(it) }
        if (environment.isNotEmpty()) {
            processBuilder.environment().putAll(environment)
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
