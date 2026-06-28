package com.w11mobile.core.environment

enum class WindowsImageArch(val labelUk: String) {
    AUTO("Авто"),
    ARM64("ARM64"),
    X86_64("x86_64"),
}

enum class WindowsBootMode {
    ISO,
    QCOW2,
}

data class WindowsImageConfig(
    val arch: WindowsImageArch,
    val bootMode: WindowsBootMode,
    val source: String,
    val isoFileName: String? = null,
    val diskFileName: String? = null,
)

object ImageArchDetector {
    fun detect(fileName: String, preference: WindowsImageArch): WindowsImageArch {
        if (preference != WindowsImageArch.AUTO) return preference

        val lower = fileName.lowercase()
        return when {
            lower.contains("arm64") ||
                lower.contains("aarch64") ||
                (lower.contains("arm") && !lower.contains("x64") && !lower.contains("amd64")) -> WindowsImageArch.ARM64

            lower.contains("x64") ||
                lower.contains("x86") ||
                lower.contains("amd64") -> WindowsImageArch.X86_64

            else -> WindowsImageArch.ARM64
        }
    }
}

object WindowsImageConfigStore {
    fun read(metaFile: java.io.File): WindowsImageConfig? {
        if (!metaFile.exists()) return null
        val map = metaFile.readLines().mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

        val source = map["source"] ?: return null
        val arch = map["arch"]?.let { runCatching { WindowsImageArch.valueOf(it) }.getOrNull() }
            ?: WindowsImageArch.ARM64
        val bootMode = when (map["boot"]) {
            "iso" -> WindowsBootMode.ISO
            "qcow2" -> WindowsBootMode.QCOW2
            else -> if (map["iso"] == "true") WindowsBootMode.ISO else WindowsBootMode.QCOW2
        }
        return WindowsImageConfig(
            arch = arch,
            bootMode = bootMode,
            source = source,
            isoFileName = map["iso_file"],
            diskFileName = map["disk_file"],
        )
    }

    fun write(metaFile: java.io.File, config: WindowsImageConfig, sizeBytes: Long) {
        metaFile.writeText(
            buildString {
                appendLine("source=${config.source}")
                appendLine("arch=${config.arch.name}")
                appendLine("boot=${config.bootMode.name.lowercase()}")
                appendLine("size=$sizeBytes")
                config.isoFileName?.let { appendLine("iso_file=$it") }
                config.diskFileName?.let { appendLine("disk_file=$it") }
            },
        )
    }
}
