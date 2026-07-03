package com.w11mobile.core.environment

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

object QemuMonitorClient {
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
            if (sendKeyOnce(host, port, key)) {
                return true
            }
        }
        return false
    }

    /** Sends a raw HMP line (e.g. `sendkey spc`) to the QEMU monitor socket. */
    fun sendRawMonitorCommand(
        command: String,
        host: String = QemuNativeLauncher.MONITOR_HOST,
        port: Int = QemuNativeLauncher.MONITOR_PORT,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                drainMonitorBanner(reader)
                val line = command.trimEnd('\n', '\r')
                writer.println(line)
                writer.flush()
                drainMonitorBanner(reader)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sendKeyOnce(host: String, port: Int, key: String): Boolean {
        return sendRawMonitorCommand("sendkey $key", host, port)
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

    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 2_000
    private const val BANNER_DRAIN_MS = 50L
}
