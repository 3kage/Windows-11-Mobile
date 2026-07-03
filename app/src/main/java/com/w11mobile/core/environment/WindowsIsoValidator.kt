package com.w11mobile.core.environment

import java.io.File
import java.io.RandomAccessFile

object WindowsIsoValidator {
    /** Win11 ARM64 retail ISO is typically 5–7 GB. */
    private const val MIN_ISO_BYTES = 3L * 1024 * 1024 * 1024

    data class Result(val ok: Boolean, val message: String)

    fun validate(isoFile: File): Result {
        if (!isoFile.isFile) {
            return Result(false, "ISO не знайдено: ${isoFile.absolutePath}")
        }
        val size = isoFile.length()
        if (size < MIN_ISO_BYTES) {
            return Result(
                ok = false,
                message = "ISO занадто малий (${size / (1024 * 1024)} MB). " +
                    "Потрібен повний Win11 ARM64 ISO (зазвичай > 5 GB), не x86 і не пошкоджений файл.",
            )
        }
        return try {
            RandomAccessFile(isoFile, "r").use { raf ->
                val bootSector = ByteArray(512)
                if (raf.read(bootSector) != bootSector.size) {
                    return Result(false, "Не вдалося прочитати ISO (пошкоджений файл?)")
                }
                val hasElTorito = bootSector.copyOfRange(0x40, 0x48).decodeToString().contains("CD001")
                if (!hasElTorito) {
                    return Result(
                        ok = false,
                        message = "Файл не схожий на bootable ISO (немає CD001). Перевірте ARM64 Win11 ISO.",
                    )
                }
            }
            Result(
                ok = true,
                message = "ISO OK: ${isoFile.name} (${size / (1024 * 1024 * 1024)} GB approx)",
            )
        } catch (error: Exception) {
            Result(false, "Помилка читання ISO: ${error.message}")
        }
    }
}
