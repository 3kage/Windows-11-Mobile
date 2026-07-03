package com.w11mobile.core.environment

import java.io.File

object WindowsIsoValidator {
    /** Win11 ARM64 retail ISO is typically 5–7 GB. */
    private const val MIN_ISO_BYTES = 1L * 1024 * 1024 * 1024

    data class Result(val ok: Boolean, val message: String)

    /**
     * Lightweight preflight only — does not block UDF-based Win11 ARM64 images.
     * Bootability is determined by UEFI/QEMU, not ISO9660 CD001 at sector 0.
     */
    fun validate(isoFile: File): Result {
        if (!isoFile.isFile) {
            return Result(false, "ISO не знайдено: ${isoFile.absolutePath}")
        }
        val size = isoFile.length()
        if (size < MIN_ISO_BYTES) {
            return Result(
                ok = false,
                message = "ISO занадто малий (${size / (1024 * 1024)} MB). " +
                    "Потрібен повний Win11 ARM64 ISO (зазвичай > 5 GB).",
            )
        }
        return Result(
            ok = true,
            message = "ISO готовий: ${isoFile.name} (~${size / (1024 * 1024 * 1024)} GB, ISO9660/UDF)",
        )
    }
}
