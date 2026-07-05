package com.w11mobile.core.environment

import org.junit.Assert.assertTrue
import org.junit.Test

class QemuEfiShellAutoKeyTest {
    @Test
    fun looksLikeStartupNshPrompt_detectsCountdown() {
        assertTrue(
            QemuEfiShellAutoKey.looksLikeStartupNshPrompt(
                "Press ESC in 3 seconds to skip startup.nsh or any other key to continue.",
            ),
        )
    }
}
