package com.w11mobile.core.environment

import android.system.Os
import android.system.OsConstants
import java.io.File

object ExecutablePreparer {
    fun installExecutable(source: File, target: File) {
        require(source.exists()) { "Файл не існує: ${source.absolutePath}" }
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        sealForExecution(target)
    }

    fun sealForExecution(file: File) {
        file.setReadable(true, false)
        file.setExecutable(true, false)
        file.setWritable(false, false)
        runCatching {
            Os.chmod(
                file.absolutePath,
                OsConstants.S_IRUSR or OsConstants.S_IXUSR or
                    OsConstants.S_IRGRP or OsConstants.S_IXGRP or
                    OsConstants.S_IROTH or OsConstants.S_IXOTH,
            )
        }
    }
}
