package com.w11mobile.core.environment

import java.io.File
import java.nio.file.Files

object RootfsEssentials {
    fun repair(rootfsDir: File) {
        val busybox = File(rootfsDir, "bin/busybox")
        if (!isRegularFile(busybox)) return

        repairBinary(rootfsDir, "bin/sh", busybox)
        repairBinary(rootfsDir, "bin/ash", busybox)
    }

    fun isReady(rootfsDir: File): Boolean {
        val busybox = File(rootfsDir, "bin/busybox")
        val shell = File(rootfsDir, "bin/sh")
        return isRegularFile(busybox) && isUsableShell(shell)
    }

    private fun isUsableShell(shell: File): Boolean {
        if (Files.isSymbolicLink(shell.toPath())) return true
        return isRegularFile(shell)
    }

    private fun isRegularFile(file: File): Boolean =
        file.exists() && Files.isRegularFile(file.toPath()) && file.length() > 0L

    private fun repairBinary(rootfsDir: File, relativePath: String, source: File) {
        val target = File(rootfsDir, relativePath)
        if (isRegularFile(target) && target.length() >= source.length()) return
        if (Files.isSymbolicLink(target.toPath())) return

        Files.deleteIfExists(target.toPath())
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
