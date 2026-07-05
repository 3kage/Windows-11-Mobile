package com.w11mobile.core.environment

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

object QemuMonitorClient {
    @Volatile
    private var awaitingMonitorWarmup = true

    fun resetSession() {
        awaitingMonitorWarmup = true
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
            if (sendRawMonitorCommand("sendkey $key", host, port)) {
                return true
            }
        }
        return false
    }

    /** Opens a persistent monitor session (preferred for rapid sendkey bursts). */
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
            val session = QemuMonitorSession.open(host, port)
            if (session != null) {
                awaitingMonitorWarmup = false
                return session
            }
        }
        return null
    }

    /** Sends a raw HMP line (e.g. `sendkey spc`) to the QEMU monitor socket. */
    fun sendRawMonitorCommand(
        command: String,
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
    ): Boolean {
        repeat(MAX_CONNECT_ATTEMPTS) { attempt ->
            if (attempt == 0 && awaitingMonitorWarmup) {
                Thread.sleep(PRE_CONNECT_DELAY_MS)
            } else if (attempt > 0) {
                Thread.sleep(CONNECT_RETRY_DELAY_MS)
            }
            if (sendRawMonitorCommandOnce(command, host, port)) {
                awaitingMonitorWarmup = false
                return true
            }
        }
        return false
    }

    private fun sendRawMonitorCommandOnce(
        command: String,
        host: String,
        port: Int,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.tcpNoDelay = true
                socket.soTimeout = READ_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()
                drainMonitorBanner(reader)
                val payload = "${command.trimEnd('\n', '\r')}\n".toByteArray(Charsets.US_ASCII)
                output.write(payload)
                output.flush()
                drainMonitorBanner(reader)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun drainMonitorBanner(reader: BufferedReader) {
        Thread.sleep(BANNER_DRAIN_MS)
        repeat(8) {
            if (!reader.ready()) {
                return
            }
            reader.readLine()
        }
    }

    private const val PRE_CONNECT_DELAY_MS = 500L
    private const val CONNECT_RETRY_DELAY_MS = 200L
    private const val MAX_CONNECT_ATTEMPTS = 3
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 2_000
    private const val PROBE_TIMEOUT_MS = 500
    private const val BANNER_DRAIN_MS = 50L
}
