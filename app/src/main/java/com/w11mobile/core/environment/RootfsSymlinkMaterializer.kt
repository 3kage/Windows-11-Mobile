package com.w11mobile.core.environment

import android.system.Os
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

object RootfsSymlinkMaterializer {
    private val ESSENTIAL_BINARIES = setOf(
        "bin/sh",
        "bin/ash",
        "bin/busybox",
    )

    fun materialize(root: File, pendingSymlinks: List<Pair<File, String>>) {
        var remaining = pendingSymlinks
        repeat(8) {
            if (remaining.isEmpty()) return
            val unresolved = mutableListOf<Pair<File, String>>()
            remaining.forEach { (target, linkName) ->
                if (!materializeOne(root, target, linkName)) {
                    unresolved += target to linkName
                }
            }
            remaining = unresolved
        }

        remaining.forEach { (target, _) ->
            if (!isEssential(root, target)) {
                deleteEntry(target)
            }
        }

        RootfsPermissions.sealGuestBinaries(root)
    }

    private fun materializeOne(root: File, target: File, linkName: String): Boolean {
        return try {
            if (isUsableEntry(target)) return true

            deleteEntry(target)

            val resolved = resolveSymlinkTarget(root, target, linkName)
            if (!resolved.isFile || resolved.length() == 0L) return false

            val relativeLink = computeRelativeLink(target, resolved)
            runCatching {
                Os.symlink(relativeLink, target.absolutePath)
            }.onSuccess {
                if (target.exists()) return true
            }

            if (isEssential(root, target)) {
                writeRegularFileCopy(target, resolved)
                RootfsPermissions.sealExecutable(target)
                return target.length() > 0L
            }

            false
        } catch (_: Exception) {
            false
        }
    }

    private fun isEssential(root: File, target: File): Boolean {
        val relative = target.relativeTo(root).path.replace('\\', '/')
        return relative in ESSENTIAL_BINARIES
    }

    private fun isUsableEntry(target: File): Boolean {
        if (!target.exists()) return false
        val path = target.toPath()
        if (Files.isSymbolicLink(path)) return true
        return Files.isRegularFile(path) && Files.size(path) > 0L
    }

    private fun deleteEntry(target: File) {
        if (!target.exists()) return
        runCatching {
            Files.deleteIfExists(target.toPath())
        }.onFailure {
            target.delete()
        }
    }

    private fun writeRegularFileCopy(target: File, source: File) {
        deleteEntry(target)
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun resolveSymlinkTarget(root: File, target: File, linkName: String): File =
        if (linkName.startsWith("/")) {
            File(root, linkName.removePrefix("/"))
        } else {
            File(target.parentFile, linkName)
        }

    private fun computeRelativeLink(from: File, to: File): String {
        val fromDir = from.parentFile?.toPath() ?: error("Symlink has no parent: ${from.path}")
        return fromDir.relativize(to.toPath()).toString().replace('\\', '/')
    }
}
