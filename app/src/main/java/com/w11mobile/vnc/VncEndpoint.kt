package com.w11mobile.vnc

import com.w11mobile.core.environment.QemuNativeLauncher

/**
 * QEMU `-display vnc=127.0.0.1:0` uses display index **:0**, which listens on TCP **5900**.
 * The display index must never be used as the socket port.
 */
object VncEndpoint {
    const val HOST: String = QemuNativeLauncher.VNC_HOST
    const val TCP_PORT: Int = QemuNativeLauncher.VNC_TCP_PORT

    fun socketPort(configuredPort: Int = TCP_PORT): Int {
        if (configuredPort != TCP_PORT && configuredPort != 0) {
            return TCP_PORT
        }
        return TCP_PORT
    }
}
