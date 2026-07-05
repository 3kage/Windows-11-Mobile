package com.w11mobile.core.environment

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Keeps a single TCP connection to the QEMU HMP monitor open for multiple commands.
 * Reconnecting for every `sendkey` is unreliable while UEFI is busy loading Boot0001.
 */
class QemuMonitorSession private constructor(
    private val socket: Socket,
    private val reader: BufferedReader,
    private val output: OutputStream,
) : AutoCloseable {

    fun sendCommand(command: String): Boolean =
        try {
            val payload = "${command.trimEnd('\n', '\r')}\n".toByteArray(Charsets.US_ASCII)
            output.write(payload)
            output.flush()
            drainMonitorResponse()
            true
        } catch (_: Exception) {
            false
        }

    override fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
            // Ignore close errors.
        }
    }

    companion object {
        fun open(
            host: String = QemuNativeLauncher.MONITOR_HOST,
            port: Int = QemuNativeLauncher.MONITOR_PORT,
            connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        ): QemuMonitorSession? =
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.tcpNoDelay = true
                socket.soTimeout = READ_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()
                drainInitialBanner(reader)
                QemuMonitorSession(socket, reader, output)
            } catch (_: Exception) {
                null
            }

        private fun drainInitialBanner(reader: BufferedReader) {
            Thread.sleep(BANNER_DRAIN_MS)
            repeat(8) {
                if (!reader.ready()) {
                    return
                }
                reader.readLine()
            }
        }

        private fun drainMonitorResponse() {
            Thread.sleep(BANNER_DRAIN_MS)
        }

        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 2_000
        private const val BANNER_DRAIN_MS = 50L
    }
}
