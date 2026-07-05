package com.w11mobile.core.environment

/**
 * Sends repeated `sendkey spc` over the shared QEMU monitor session.
 */
object QemuMonitorSpaceSpam {

    fun spamSpace(
        durationMs: Long,
        intervalMs: Long,
        pause: (Long) -> Unit,
    ): Int {
        var sent = 0
        val deadlineMs = System.currentTimeMillis() + durationMs
        var consecutiveFailures = 0
        while (System.currentTimeMillis() < deadlineMs) {
            if (QemuMonitorClient.sendMonitorCommand("sendkey spc")) {
                sent += 1
                consecutiveFailures = 0
            } else {
                consecutiveFailures += 1
                if (consecutiveFailures >= 3) {
                    QemuMonitorClient.closeSharedSession()
                    consecutiveFailures = 0
                }
            }
            pause(intervalMs)
        }
        return sent
    }
}
