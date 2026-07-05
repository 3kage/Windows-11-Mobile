package com.w11mobile.core.environment

/**
 * Sends repeated `sendkey spc` over one persistent monitor connection.
 */
object QemuMonitorSpaceSpam {

    fun spamSpace(
        durationMs: Long,
        intervalMs: Long,
        pause: (Long) -> Unit,
    ): Int {
        var sent = 0
        var session = QemuMonitorClient.openSession()
        val deadlineMs = System.currentTimeMillis() + durationMs
        var consecutiveFailures = 0
        try {
            while (System.currentTimeMillis() < deadlineMs) {
                val ok = sendSpace(session)
                if (ok) {
                    sent += 1
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= 3) {
                        session?.close()
                        session = QemuMonitorClient.openSession()
                        consecutiveFailures = 0
                    }
                }
                pause(intervalMs)
            }
        } finally {
            session?.close()
        }
        return sent
    }

    private fun sendSpace(session: QemuMonitorSession?): Boolean =
        if (session != null) {
            session.sendCommand("sendkey spc")
        } else {
            QemuMonitorClient.sendRawMonitorCommand("sendkey spc")
        }
}
