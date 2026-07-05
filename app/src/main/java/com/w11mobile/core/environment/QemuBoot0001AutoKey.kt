package com.w11mobile.core.environment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When UEFI logs `starting Boot0001`, spam `sendkey spc` via QEMU monitor for 5 s
 * (every 400 ms) to hit "Press any key to boot from CD..." regardless of timing.
 */
object QemuBoot0001AutoKey {
    private val scheduled = AtomicBoolean(false)

    fun reset() {
        scheduled.set(false)
    }

    fun onTerminalLine(line: String, scope: CoroutineScope) {
        if (!isStartingBoot0001Line(line)) {
            return
        }
        if (!scheduled.compareAndSet(false, true)) {
            return
        }
        scope.launch(Dispatchers.IO) {
            QemuRuntimeEvents.publishStatus(
                "Boot0001 — sendkey spc кожні ${SPAM_INTERVAL_MS}ms протягом ${SPAM_DURATION_MS / 1000} с…",
            )
            val deadlineMs = System.currentTimeMillis() + SPAM_DURATION_MS
            var sent = 0
            while (System.currentTimeMillis() < deadlineMs) {
                if (QemuMonitorClient.sendRawMonitorCommand("sendkey spc")) {
                    sent += 1
                }
                delay(SPAM_INTERVAL_MS)
            }
            QemuRuntimeEvents.publishStatus(
                "Boot0001 sendkey spc завершено ($sent×) → " +
                    "${QemuNativeLauncher.MONITOR_HOST}:${QemuNativeLauncher.MONITOR_PORT}",
            )
        }
    }

    private const val SPAM_DURATION_MS = 5_000L
    private const val SPAM_INTERVAL_MS = 400L

    internal fun isStartingBoot0001Line(line: String): Boolean =
        line.contains("starting Boot0001", ignoreCase = true)
}
