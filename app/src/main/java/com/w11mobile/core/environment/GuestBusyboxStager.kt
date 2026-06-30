package com.w11mobile.core.environment

import java.io.File

object GuestBusyboxStager {
    fun ensureReady(paths: AppPaths): File {
        if (isUsable(paths.guestBusybox)) {
            return paths.guestBusybox
        }

        val staged = File(paths.guestExecDir, "busybox")
        if (isUsable(staged)) {
            return staged
        }

        val rootfsBusybox = File(paths.rootfsDir, "bin/busybox")
        require(isUsable(rootfsBusybox)) {
            "Alpine busybox не знайдено. Спочатку завершіть завантаження rootfs."
        }

        ExecutablePreparer.installExecutable(rootfsBusybox, staged)
        return staged
    }

    private fun isUsable(file: File): Boolean =
        file.exists() && file.isFile && file.length() > 0L
}
