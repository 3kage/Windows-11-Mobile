package com.w11mobile.core.environment

object GuestShell {
    fun wrap(paths: AppPaths, command: String): String = """
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export LD_LIBRARY_PATH=${paths.libDir.absolutePath}
        $command
    """.trimIndent()

    fun termuxBinary(paths: AppPaths, guestBinaryName: String, arguments: String): String =
        "/system/bin/linker64 /exec/guest/$guestBinaryName $arguments"
}
