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
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2_000)
                socket.soTimeout = 2_000
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println("sendkey $key")
                while (reader.ready()) {
                    reader.readLine()
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
