package com.w11mobile.vnc

import com.w11mobile.core.environment.QemuNativeLauncher
import org.junit.Assert.assertEquals
import org.junit.Test

class VncEndpointTest {
    @Test
    fun socketPort_alwaysUsesTcp5900_notDisplayIndex() {
        assertEquals(5900, VncEndpoint.socketPort(0))
        assertEquals(5900, VncEndpoint.socketPort(QemuNativeLauncher.VNC_DISPLAY_INDEX))
        assertEquals(5900, VncEndpoint.socketPort(VncEndpoint.TCP_PORT))
    }
}
