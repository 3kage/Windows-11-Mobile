package com.w11mobile.vnc

import com.w11mobile.core.environment.QemuNativeLauncher
import java.net.InetSocketAddress
import java.net.Socket

object VncPortProbe {
    fun isOpen(
        host: String = QemuNativeLauncher.VNC_HOST,
        port: Int = QemuNativeLauncher.VNC_PORT,
        timeoutMs: Int = 500,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
