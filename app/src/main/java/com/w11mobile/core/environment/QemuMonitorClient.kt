package com.w11mobile.core.environment

import java.net.InetSocketAddress
import java.net.Socket

object QemuMonitorClient {
    private val sessionLock = Any()

    @Volatile
    private var sharedSession: QemuMonitorSession? = null

    @Volatile
    private var awaitingMonitorWarmup = true

    fun resetSession() {
        closeSharedSession()
        awaitingMonitorWarmup = true
    }

    fun closeSharedSession() {
        synchronized(sessionLock) {
            try {
                sharedSession?.close()
            } catch (_: Exception) {
                // Ignore close errors.
            }
            sharedSession = null
        }
    }

    fun isMonitorReachable(
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
        timeoutMs: Int = PROBE_TIMEOUT_MS,
    ): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }

    fun sendKey(
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
        key: String = "spc",
    ): Boolean = sendKeyWithRetries(
        host = host,
        port = port,
        key = key,
        maxAttempts = 1,
        retryDelayMs = 0L,
    )

    fun sendKeyWithRetries(
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
        key: String = "spc",
        maxAttempts: Int = 12,
        retryDelayMs: Long = 1_000L,
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            if (attempt > 0 && retryDelayMs > 0L) {
                Thread.sleep(retryDelayMs)
            }
            if (sendMonitorCommand("sendkey $key", host, port)) {
                return true
            }
        }
        return false
    }

    /**
     * Sends a command over a shared persistent monitor socket (reused across UI taps and boot spam).
     */
    fun sendMonitorCommand(
        command: String,
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
        waitForPortMs: Long = 0L,
    ): Boolean {
        waitForMonitorPort(host, port, waitForPortMs)
        repeat(MAX_CONNECT_ATTEMPTS) { attempt ->
            if (attempt == 0 && awaitingMonitorWarmup) {
                Thread.sleep(PRE_CONNECT_DELAY_MS)
            } else if (attempt > 0) {
                Thread.sleep(CONNECT_RETRY_DELAY_MS)
            }
            val session = obtainSharedSession(host, port)
            if (session != null && session.sendCommand(command)) {
                awaitingMonitorWarmup = false
                return true
            }
            invalidateSharedSession()
        }
        return false
    }

    /** @see sendMonitorCommand */
    fun sendRawMonitorCommand(
        command: String,
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
    ): Boolean = sendMonitorCommand(command, host, port)

    /** Returns the shared session without closing it (for burst loops). */
    fun openSession(
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
    ): QemuMonitorSession? {
        repeat(MAX_CONNECT_ATTEMPTS) { attempt ->
            if (attempt == 0 && awaitingMonitorWarmup) {
                Thread.sleep(PRE_CONNECT_DELAY_MS)
            } else if (attempt > 0) {
                Thread.sleep(CONNECT_RETRY_DELAY_MS)
            }
            val session = obtainSharedSession(host, port)
            if (session != null) {
                awaitingMonitorWarmup = false
                return session
            }
            invalidateSharedSession()
        }
        return null
    }

    private fun waitForMonitorPort(host: String, port: Int, waitForPortMs: Long) {
        if (waitForPortMs <= 0L) {
            return
        }
        var waitedMs = 0L
        while (waitedMs < waitForPortMs && !isMonitorReachable(host, port)) {
            Thread.sleep(MONITOR_POLL_MS)
            waitedMs += MONITOR_POLL_MS
        }
    }

    private fun obtainSharedSession(host: String, port: Int): QemuMonitorSession? {
        synchronized(sessionLock) {
            sharedSession?.let { return it }
            val session = QemuMonitorSession.open(host, port) ?: return null
            sharedSession = session
            return session
        }
    }

    private fun invalidateSharedSession() {
        synchronized(sessionLock) {
            try {
                sharedSession?.close()
            } catch (_: Exception) {
                // Ignore close errors.
            }
            sharedSession = null
        }
    }

    private const val PRE_CONNECT_DELAY_MS = 500L
    private const val CONNECT_RETRY_DELAY_MS = 200L
    private const val MAX_CONNECT_ATTEMPTS = 5
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 2_000
    private const val PROBE_TIMEOUT_MS = 500
    private const val MONITOR_POLL_MS = 200L
}
