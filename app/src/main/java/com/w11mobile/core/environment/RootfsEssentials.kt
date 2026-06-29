package com.w11mobile.core.environment

import java.io.File
import java.nio.file.Files

object RootfsEssentials {
    fun repair(rootfsDir: File) {
        val busybox = File(rootfsDir, "bin/busybox")
        if (!isRegularFile(busybox)) return

        repairBinary(rootfsDir, "bin/sh", busybox)
        repairBinary(rootfsDir, "bin/ash", busybox)
        RootfsPermissions.sealGuestBinaries(rootfsDir)
    }

    fun isReady(rootfsDir: File): Boolean {
        val busybox = File(rootfsDir, "bin/busybox")
        val shell = File(rootfsDir, "bin/sh")
        if (!RootfsPermissions.isExecutableForGuest(busybox)) return false
        if (!shell.exists()) return false
        if (Files.isSymbolicLink(shell.toPath())) return true
        return RootfsPermissions.isExecutableForGuest(shell)
    }

    private fun isRegularFile(file: File): Boolean =
        file.exists() && Files.isRegularFile(file.toPath()) && file.length() > 0L

    private fun repairBinary(rootfsDir: File, relativePath: String, source: File) {
        val target = File(rootfsDir, relativePath)
        val needsCopy = !Files.isSymbolicLink(target.toPath()) &&
            (!isRegularFile(target) || target.length() < source.length())

        if (needsCopy) {
            Files.deleteIfExists(target.toPath())
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        if (!Files.isSymbolicLink(target.toPath()) && target.exists()) {
            RootfsPermissions.sealExecutable(target)
        }
    }
}
