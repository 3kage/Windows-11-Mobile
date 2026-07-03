package com.w11mobile.vnc

import com.w11mobile.core.environment.QemuNativeLauncher

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
        VncConnectionDiagnostics.probePort(host, port, timeoutMs)
}
