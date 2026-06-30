package com.w11mobile.core.environment

import java.io.File

object EnvironmentAssets {
    /** Increment only when bundled rootfs/Termux/QEMU payloads change on disk. */
    const val ASSETS_VERSION = 1
}

object EnvironmentReadiness {
    fun hasRootfs(paths: AppPaths): Boolean =
        File(paths.rootfsDir, "bin/sh").exists()

    fun hasTermuxLibraries(paths: AppPaths): Boolean =
        paths.libDir.isDirectory &&
            paths.libDir.listFiles()?.any { file -> file.isFile && file.length() > 0L } == true

    fun isAssetsVersionCurrent(lastAssetsVersion: Int): Boolean =
        lastAssetsVersion == EnvironmentAssets.ASSETS_VERSION

    fun isAssetsVersionCurrent(preferences: SetupPreferences): Boolean =
        isAssetsVersionCurrent(preferences.lastAssetsVersion)

    fun isPersistedEnvironmentReady(
        lastAssetsVersion: Int,
        setupComplete: Boolean,
        paths: AppPaths,
    ): Boolean {
        if (!hasRootfs(paths) || !hasTermuxLibraries(paths)) {
            return false
        }
        if (isAssetsVersionCurrent(lastAssetsVersion)) {
            return true
        }
        return setupComplete
    }

    fun isPersistedEnvironmentReady(
        preferences: SetupPreferences,
        paths: AppPaths,
    ): Boolean = isPersistedEnvironmentReady(
        lastAssetsVersion = preferences.lastAssetsVersion,
        setupComplete = preferences.setupComplete,
        paths = paths,
    )

    fun isQemuRuntimeReady(paths: AppPaths): Boolean =
        paths.qemuNativeLib.exists() &&
            paths.qemuNativeLib.length() > 0L &&
            paths.uefiFirmware.exists() &&
            paths.uefiFirmware.length() > 0L &&
            paths.qemuVirtioRom.exists() &&
            paths.qemuVirtioRom.length() > 0L
}
