package com.w11mobile.core.environment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When UEFI logs `starting Boot0001`, send `sendkey spc` via QEMU monitor after 1 s
 * to hit the Windows ISO "Press any key to boot from CD..." window.
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
            delay(BOOT_DELAY_MS)
            repeat(MAX_ATTEMPTS) { attempt ->
                if (attempt > 0) {
                    delay(RETRY_MS)
                }
                if (QemuMonitorClient.sendRawMonitorCommand("sendkey spc")) {
                    QemuRuntimeEvents.publishStatus(
                        "sendkey spc після Boot0001 (+${BOOT_DELAY_MS}ms) → " +
                            "${QemuNativeLauncher.MONITOR_HOST}:${QemuNativeLauncher.MONITOR_PORT}",
                    )
                    return@launch
                }
            }
            QemuRuntimeEvents.publishStatus(
                "Не вдалося sendkey spc після Boot0001 (monitor ${QemuNativeLauncher.MONITOR_HOST}:" +
                    "${QemuNativeLauncher.MONITOR_PORT})",
            )
        }
    }

    private const val BOOT_DELAY_MS = 1_000L
    private const val RETRY_MS = 2_000L
    private const val MAX_ATTEMPTS = 8

    internal fun isStartingBoot0001Line(line: String): Boolean =
        line.contains("starting Boot0001", ignoreCase = true)
}
