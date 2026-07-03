package com.w11mobile.core.environment

/**
 * Detects Windows ISO "Press any key" prompts in QEMU serial output.
 */
class QemuIsoBootKeyInjector {
    @Volatile
    private var keySent = false

    @Volatile
    private var isoBootFailed = false

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
        // no-op; kept for QemuManager lifecycle symmetry
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
}
