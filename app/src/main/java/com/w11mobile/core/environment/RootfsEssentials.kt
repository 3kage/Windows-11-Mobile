package com.w11mobile.core.environment

import java.io.File

object RootfsEssentials {
    fun repair(rootfsDir: File) {
        val busybox = File(rootfsDir, "bin/busybox")
        if (!busybox.isFile || busybox.length() == 0L) return

        repairBinary(rootfsDir, "bin/sh", busybox)
        repairBinary(rootfsDir, "bin/ash", busybox)
    }

    fun isReady(rootfsDir: File): Boolean {
        val busybox = File(rootfsDir, "bin/busybox")
        val shell = File(rootfsDir, "bin/sh")
        return busybox.isFile &&
            busybox.length() > 0L &&
            shell.isFile &&
            shell.length() > 0L
    }

    private fun repairBinary(rootfsDir: File, relativePath: String, source: File) {
        val target = File(rootfsDir, relativePath)
        if (target.isFile && target.length() >= source.length()) return
        source.copyTo(target, overwrite = true)
    }
}
