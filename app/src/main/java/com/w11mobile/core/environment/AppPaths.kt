package com.w11mobile.core.environment

import java.io.File

class AppPaths(filesDir: File) {
    val baseDir: File = filesDir
    val cacheDir: File = File(filesDir, "cache").apply { mkdirs() }
    val termuxPrefix: File = File(filesDir, "termux/usr").apply { mkdirs() }
    val binDir: File = File(termuxPrefix, "bin").apply { mkdirs() }
    val libexecDir: File = File(termuxPrefix, "libexec").apply { mkdirs() }
    val proot: File = File(binDir, "proot")
    val rootfsDir: File = File(filesDir, "rootfs").apply { mkdirs() }
    val imagesDir: File = File(filesDir, "images").apply { mkdirs() }
    val windowsImage: File = File(imagesDir, "windows.qcow2")
    val windowsImageMeta: File = File(imagesDir, "windows.meta")
}
