package com.w11mobile.core.environment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When UEFI logs `starting Boot0001`, repeatedly send `sendkey spc` via QEMU monitor
 * to hit "Press any key to boot from CD..." during slow UDF ISO loads on phone storage.
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
                "Boot0001 стартує — автоматичні sendkey spc протягом 60 с…",
            )
            var lastDelay = 0L
            for (targetDelay in SEND_AT_MS) {
                delay(targetDelay - lastDelay)
                lastDelay = targetDelay
                if (QemuMonitorClient.sendRawMonitorCommand("sendkey spc")) {
                    QemuRuntimeEvents.publishStatus(
                        "sendkey spc (+${targetDelay}ms після Boot0001) → " +
                            "${QemuNativeLauncher.MONITOR_HOST}:${QemuNativeLauncher.MONITOR_PORT}",
                    )
                }
            }
        }
    }

    /** Delays from Boot0001 start (ms). First send at 1 s as requested. */
    private val SEND_AT_MS = longArrayOf(
        1_000L, 3_000L, 6_000L, 10_000L, 15_000L, 22_000L, 30_000L, 40_000L, 55_000L,
    )

    internal fun isStartingBoot0001Line(line: String): Boolean =
        line.contains("starting Boot0001", ignoreCase = true)
}
