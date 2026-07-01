package com.w11mobile.core.environment

/**
 * Windows install ISO shows "Press any key to boot from CD/DVD" for a few seconds.
 * Without a keypress UEFI times out and drops to the EFI shell.
 */
class QemuIsoBootKeyInjector {
    @Volatile
    private var keySent = false

    private var helperThread: Thread? = null

    fun onBootStarted() {
        helperThread = Thread(
            {
                try {
                    Thread.sleep(INITIAL_DELAY_MS)
                    repeat(MAX_ATTEMPTS) {
                        if (keySent) {
                            return@Thread
                        }
                        if (QemuMonitorClient.sendKeyWithRetries(maxAttempts = 3, retryDelayMs = 500L)) {
                            keySent = true
                            return@Thread
                        }
                        Thread.sleep(RETRY_INTERVAL_MS)
                    }
                } catch (_: InterruptedException) {
                    // cancelled when QEMU exits
                }
            },
            "qemu-iso-boot-key",
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun onOutputLine(line: String) {
        if (keySent || !looksLikePressAnyKeyPrompt(line)) {
            return
        }
        if (QemuMonitorClient.sendKeyWithRetries(maxAttempts = 5, retryDelayMs = 200L)) {
            keySent = true
        }
    }

    fun stop() {
        helperThread?.interrupt()
        helperThread = null
    }

    internal fun looksLikePressAnyKeyPrompt(line: String): Boolean {
        val normalized = line.replace(Regex("[\\u001B\\u009B][\\[\\]()#;?]*(?:(?:[a-zA-Z\\d])*(?:;[a-zA-Z\\d])*)?[0-9A-ORZcf-ntqry=><~]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\p{C}"), " ")
        return normalized.contains("Press any key", ignoreCase = true) ||
            normalized.contains("boot from CD", ignoreCase = true) ||
            normalized.contains("boot from DVD", ignoreCase = true)
    }

    companion object {
        private const val INITIAL_DELAY_MS = 3_000L
        private const val RETRY_INTERVAL_MS = 2_000L
        private const val MAX_ATTEMPTS = 8
    }
}
