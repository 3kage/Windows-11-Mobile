package com.w11mobile.core.environment

import java.io.File

class AppPaths(
    filesDir: File,
    codeCacheDir: File,
    nativeLibraryDir: String,
) {
    val baseDir: File = filesDir
    val cacheDir: File = File(filesDir, "cache").apply { mkdirs() }
    val execDir: File = File(codeCacheDir, "exec").apply { mkdirs() }
    val guestExecDir: File = File(codeCacheDir, "exec/guest").apply { mkdirs() }
    val guestBusybox: File = File(nativeLibraryDir, "libalpine_busybox.so")
    val termuxRoot: File = File(filesDir, "termux").apply { mkdirs() }
    val termuxPrefix: File = File(termuxRoot, "usr").apply { mkdirs() }
    val binDir: File = File(termuxPrefix, "bin").apply { mkdirs() }
    val libexecDir: File = File(termuxPrefix, "libexec").apply { mkdirs() }
    val libDir: File = File(termuxPrefix, "lib").apply { mkdirs() }
    val prootTmpDir: File = File(termuxPrefix, "tmp").apply { mkdirs() }
    val extractedProot: File = File(binDir, "proot")
    val proot: File = File(execDir, "proot")
    val prootLoader: File = File(execDir, "proot-loader")
    val rootfsDir: File = File(filesDir, "rootfs").apply { mkdirs() }
    val imagesDir: File = File(filesDir, "images").apply { mkdirs() }
    val windowsIso: File = File(imagesDir, "windows.iso")
    val windowsDisk: File = File(imagesDir, "windows-disk.qcow2")
    val windowsImage: File = File(imagesDir, "windows.qcow2")
    val windowsImageMeta: File = File(imagesDir, "windows.meta")

    fun readImageConfig(): WindowsImageConfig? = WindowsImageConfigStore.read(windowsImageMeta)

    fun hasBootableImage(): Boolean = windowsIso.exists() || windowsImage.exists() || windowsDisk.exists()
}
