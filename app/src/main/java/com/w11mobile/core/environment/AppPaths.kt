package com.w11mobile.core.environment

import java.io.File

class AppPaths(
    filesDir: File,
    applicationCacheDir: File,
    codeCacheDir: File,
    nativeLibraryDir: String,
) {
    val baseDir: File = filesDir
    /** Internal download / marker cache under files/. */
    val cacheDir: File = File(filesDir, "cache").apply { mkdirs() }
    /** context.cacheDir — used for PROOT_TMPDIR. */
    val appCacheDir: File = applicationCacheDir
    val prootTmpDir: File = File(applicationCacheDir, "proot-tmp").apply { mkdirs() }
    val guestExecDir: File = File(codeCacheDir, "exec/guest").apply { mkdirs() }
    val guestBusybox: File = File(nativeLibraryDir, "libalpine_busybox.so")
    val prootNativeLib: File = File(nativeLibraryDir, "libproot.so")
    val prootLoaderNativeLib: File = File(nativeLibraryDir, "libproot_loader.so")
    val qemuNativeLib: File = File(nativeLibraryDir, "libqemu.so")
    val qemuImgNativeLib: File = File(nativeLibraryDir, "libqemu_img.so")
    val uefiFirmwareDir: File = File(filesDir, "firmware").apply { mkdirs() }
    val uefiFirmware: File = File(uefiFirmwareDir, "QEMU_EFI.fd")
    val termuxRoot: File = File(filesDir, "termux").apply { mkdirs() }
    val termuxPrefix: File = File(termuxRoot, "usr").apply { mkdirs() }
    val binDir: File = File(termuxPrefix, "bin").apply { mkdirs() }
    val libexecDir: File = File(termuxPrefix, "libexec").apply { mkdirs() }
    val libDir: File = File(termuxPrefix, "lib").apply { mkdirs() }
    /** App-managed QEMU firmware/ROM directory passed to libqemu.so via -L. */
    val qemuShareDir: File = File(filesDir, "qemu/share").apply { mkdirs() }
    /** Termux extract path (fallback source for ROM files). */
    val termuxQemuShareDir: File = File(termuxPrefix, "share/qemu")
    val qemuVirtioRom: File = File(qemuShareDir, "efi-virtio.rom")
    val extractedProot: File = File(binDir, "proot")
    val rootfsDir: File = File(filesDir, "rootfs").apply { mkdirs() }
    val imagesDir: File = File(filesDir, "images").apply { mkdirs() }
    val windowsIso: File = File(imagesDir, "windows.iso")
    val windowsDisk: File = File(imagesDir, "windows-disk.qcow2")
    val windowsImage: File = File(imagesDir, "windows.qcow2")
    val windowsImageMeta: File = File(imagesDir, "windows.meta")

    fun readImageConfig(): WindowsImageConfig? = WindowsImageConfigStore.read(windowsImageMeta)

    fun hasBootableImage(): Boolean = windowsIso.exists() || windowsImage.exists() || windowsDisk.exists()
}
