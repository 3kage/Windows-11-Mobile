package com.w11mobile.core.environment

import java.lang.IllegalThreadStateException

/**
 * Tracks the host [Process] started for libqemu.so so the VNC UI can detect early exit.
 */
object QemuProcessSession {
    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var lastExitCode: Int? = null

    @Volatile
    private var launchStarted: Boolean = false

    fun markLaunchStarting() {
        synchronized(this) {
            activeProcess = null
            lastExitCode = null
            launchStarted = true
        }
        QemuMonitorClient.resetSession()
    }

    fun attach(process: Process) {
        synchronized(this) {
            activeProcess = process
            lastExitCode = null
            launchStarted = true
        }
    }

    fun complete(exitCode: Int) {
        synchronized(this) {
            activeProcess = null
            lastExitCode = exitCode
        }
    }

    fun reset() {
        synchronized(this) {
            activeProcess = null
            lastExitCode = null
            launchStarted = false
        }
    }

    fun isLaunchStarted(): Boolean = launchStarted

    fun isAlive(): Boolean = activeProcess?.isAlive == true

    /**
     * Returns an exit code when QEMU is no longer running after launch began.
     */
    fun resolvedExitCodeOrNull(): Int? {
        synchronized(this) {
            if (!launchStarted) {
                return null
            }
            val process = activeProcess
            if (process != null) {
                if (process.isAlive) {
                    return null
                }
                return readExitCode(process)
            }
            return lastExitCode
        }
    }

    fun hasProcessExited(): Boolean = resolvedExitCodeOrNull() != null

    private fun readExitCode(process: Process): Int? {
        return try {
            process.exitValue()
        } catch (_: IllegalThreadStateException) {
            null
        }
    }
}
