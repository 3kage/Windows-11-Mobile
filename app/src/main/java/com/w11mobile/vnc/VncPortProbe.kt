package com.w11mobile.vnc

import com.w11mobile.core.environment.QemuNativeLauncher
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.Socket

object VncPortProbe {
    fun isOpen(
        host: String = QemuNativeLauncher.VNC_HOST,
        port: Int = QemuNativeLauncher.VNC_PORT,
        timeoutMs: Int = 500,
    ): Boolean = probe(host, port, timeoutMs).open

    fun probe(
        host: String = QemuNativeLauncher.VNC_HOST,
        port: Int = QemuNativeLauncher.VNC_PORT,
        timeoutMs: Int = 500,
    ): VncConnectionDiagnostics.ProbeResult =
        VncConnectionDiagnostics.probeRfb(host, port, timeoutMs)
}
