package com.w11mobile.core.environment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When UEFI logs `starting Boot0001`, spam `sendkey spc` for up to 90 s while the
 * ARM64 UDF ISO loader runs — the "Press any key" prompt often appears much later.
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
            Thread.sleep(BOOT0001_SETTLE_MS)
            QemuRuntimeEvents.publishStatus(
                "Boot0001 — очікування QEMU monitor ${QemuNativeLauncher.MONITOR_PORT}…",
            )
            var waitedMs = 0L
            while (waitedMs < MONITOR_WAIT_MS && !QemuMonitorClient.isMonitorReachable()) {
                delay(MONITOR_POLL_MS)
                waitedMs += MONITOR_POLL_MS
            }
            QemuRuntimeEvents.publishStatus(
                "Boot0001 — sendkey spc кожні ${SPAM_INTERVAL_MS}ms протягом ${SPAM_DURATION_MS / 1000} с " +
                    "(UDF ISO може завантажуватися довго)…",
            )
            val sent = QemuMonitorSpaceSpam.spamSpace(SPAM_DURATION_MS, SPAM_INTERVAL_MS) { ms ->
                Thread.sleep(ms)
            }
            QemuRuntimeEvents.publishStatus(
                "Boot0001 sendkey spc завершено ($sent×) → " +
                    "${QemuNativeLauncher.MONITOR_HOST}:${QemuNativeLauncher.MONITOR_PORT}",
            )
        }
    }

    private const val BOOT0001_SETTLE_MS = 1_000L
    private const val MONITOR_WAIT_MS = 10_000L
    private const val MONITOR_POLL_MS = 200L
    private const val SPAM_DURATION_MS = 90_000L
    private const val SPAM_INTERVAL_MS = 250L

    internal fun isStartingBoot0001Line(line: String): Boolean =
        line.contains("starting Boot0001", ignoreCase = true)
}
