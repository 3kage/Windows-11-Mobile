package com.w11mobile.core.environment

import java.io.File

object QemuSharedLibraryRequirements {
    /** Bionic .so names required by libqemu_img.so when launched via linker64. */
    val REQUIRED_LIBS: List<String> = listOf(
        "libzstd.so.1",
    )

    fun missingLibraries(libDir: File): List<String> =
        REQUIRED_LIBS.filter { name ->
            val file = File(libDir, name)
            !file.isFile || file.length() == 0L
        }

    fun hasRequiredLibraries(libDir: File): Boolean = missingLibraries(libDir).isEmpty()
}
