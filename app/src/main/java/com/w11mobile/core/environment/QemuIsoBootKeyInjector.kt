package com.w11mobile.core.environment

/**
 * Windows install ISO shows "Press any key to boot from CD/DVD" for a few seconds.
 * Without a keypress UEFI times out and drops to the EFI shell.
 */
class QemuIsoBootKeyInjector {
    @Volatile
    private var keySent = false

    @Volatile
    private var isoBootFailed = false

    private var helperThread: Thread? = null

    fun onBootStarted() {
        helperThread = Thread(
            {
                try {
                    Thread.sleep(INITIAL_DELAY_MS)
                    repeat(MAX_ATTEMPTS) {
                        if (keySent || isoBootFailed) {
                            return@Thread
                        }
                        if (QemuMonitorClient.sendKeyWithRetries(maxAttempts = 3, retryDelayMs = 500L)) {
                            keySent = true
                            QemuRuntimeEvents.publishStatus(
                                "Автонатиск «Будь-яка клавіша» надіслано в QEMU monitor",
                            )
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
        if (looksLikeIsoBootFailure(line)) {
            isoBootFailed = true
            QemuRuntimeEvents.publishStatus(
                "ISO не завантажився (UEFI timeout). Перевірте повний Win11 ARM64 ISO (>5 GB), не x86.",
            )
            return
        }
        if (keySent || isoBootFailed || !looksLikePressAnyKeyPrompt(line)) {
            return
        }
        if (QemuMonitorClient.sendKeyWithRetries(maxAttempts = 5, retryDelayMs = 200L)) {
            keySent = true
            QemuRuntimeEvents.publishStatus(
                "«Будь-яка клавіша» надіслано (виявлено підказку в serial-логу)",
            )
        }
    }

    fun stop() {
        helperThread?.interrupt()
        helperThread = null
    }

    internal fun looksLikeIsoBootFailure(line: String): Boolean {
        val normalized = normalizeLine(line)
        return normalized.contains("failed to start Boot0001", ignoreCase = true) ||
            normalized.contains("UEFI Interactive Shell", ignoreCase = true)
    }

    internal fun looksLikePressAnyKeyPrompt(line: String): Boolean {
        val normalized = normalizeLine(line)
        if (normalized.contains("startup.nsh", ignoreCase = true) ||
            normalized.contains("Press ESC", ignoreCase = true) ||
            normalized.contains("UEFI Interactive Shell", ignoreCase = true)
        ) {
            return false
        }
        return normalized.contains("Press any key", ignoreCase = true) ||
            normalized.contains("boot from CD", ignoreCase = true) ||
            normalized.contains("boot from DVD", ignoreCase = true)
    }

    private fun normalizeLine(line: String): String =
        line.replace(
            Regex(
                "[\\u001B\\u009B][\\[\\]()#;?]*(?:(?:[a-zA-Z\\d])*(?:;[a-zA-Z\\d])*)?[0-9A-ORZcf-ntqry=><~]",
                RegexOption.IGNORE_CASE,
            ),
            "",
        ).replace(Regex("\\p{C}"), " ")

    companion object {
        /** Wait for virtio CD + Windows bootmgr before blind sendkey attempts. */
        private const val INITIAL_DELAY_MS = 20_000L
        private const val RETRY_INTERVAL_MS = 3_000L
        private const val MAX_ATTEMPTS = 6
    }
}
