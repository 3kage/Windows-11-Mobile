package com.w11mobile.core.environment

import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File

object SharedLibraryMaterializer {
    fun materialize(libDir: File) {
        if (!libDir.isDirectory) return

        libDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".so") && it.length() == 0L }
            ?.forEach { it.delete() }

        libDir.listFiles()
            ?.filter { it.isFile && it.name.contains(".so.") && it.length() > 0L }
            ?.forEach { versionedLib ->
                val majorLibName = versionedLib.name.substringBeforeLast('.')
                copyLibrary(versionedLib, File(libDir, majorLibName))

                if (majorLibName.contains(".so.")) {
                    val baseLibName = majorLibName.substringBefore(".so.") + ".so"
                    copyLibrary(versionedLib, File(libDir, baseLibName))
                }
            }
    }

    private fun copyLibrary(source: File, target: File) {
        if (target.exists() && target.length() >= source.length()) return
        source.copyTo(target, overwrite = true)
    }
}
