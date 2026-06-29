package com.w11mobile.core.environment

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import org.apache.commons.compress.archivers.tar.TarArchiveEntry

object RootfsPermissions {
    fun sealExecutable(file: File) {
        if (!file.exists() || Files.isSymbolicLink(file.toPath())) return
        file.setReadable(true, false)
        file.setExecutable(true, false)
        file.setWritable(true, true)
        runCatching {
            Os.chmod(
                file.absolutePath,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR or
                    OsConstants.S_IRGRP or OsConstants.S_IXGRP or
                    OsConstants.S_IROTH or OsConstants.S_IXOTH,
            )
        }
    }

    fun applyTarMode(file: File, entry: TarArchiveEntry) {
        if (entry.isDirectory || entry.isSymbolicLink) return
        runCatching {
            Os.chmod(file.absolutePath, entry.mode and 4095)
        }.onFailure {
            if (entry.mode and 64 != 0) {
                sealExecutable(file)
            }
        }
    }

    fun isExecutableForGuest(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (Files.isSymbolicLink(file.toPath())) return true
        if (file.canExecute()) return true
        return runCatching {
            Files.getPosixFilePermissions(file.toPath())
                .any { it == PosixFilePermission.OWNER_EXECUTE || it == PosixFilePermission.GROUP_EXECUTE || it == PosixFilePermission.OTHERS_EXECUTE }
        }.getOrDefault(false)
    }

    fun sealGuestBinaries(rootfsDir: File) {
        listOf(
            "bin/busybox",
            "bin/sh",
            "bin/ash",
            "sbin/init",
        ).forEach { relativePath ->
            sealExecutable(File(rootfsDir, relativePath))
        }
    }
}
