package com.w11mobile.core.environment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sends `sendkey spc` when UEFI shows the EFI Shell startup.nsh countdown. */
object QemuEfiShellAutoKey {
    private var active = false

    fun reset() {
        active = false
    }

    fun onTerminalLine(line: String, scope: CoroutineScope) {
        if (!looksLikeStartupNshPrompt(line)) {
            return
        }
        if (active) {
            return
        }
        active = true
        scope.launch(Dispatchers.IO) {
            delay(300)
            if (QemuMonitorClient.sendRawMonitorCommand("sendkey spc")) {
                QemuRuntimeEvents.publishStatus(
                    "sendkey spc для EFI Shell / startup.nsh → " +
                        "${QemuNativeLauncher.MONITOR_HOST}:${QemuNativeLauncher.MONITOR_PORT}",
                )
            }
        }
    }

    internal fun looksLikeStartupNshPrompt(line: String): Boolean {
        val normalized = line.replace(Regex("\\p{C}"), " ")
        return normalized.contains("skip startup.nsh", ignoreCase = true) ||
            normalized.contains("Press ESC", ignoreCase = true) &&
            normalized.contains("startup.nsh", ignoreCase = true)
    }
}
