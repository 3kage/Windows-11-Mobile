package com.w11mobile.core.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnvironmentReadinessTest {
    @Test
    fun isPersistedEnvironmentReady_requiresRootfsAndTermuxLibs() {
        val root = createTempDir("env")
        val paths = testPaths(root)

        assertFalse(
            EnvironmentReadiness.isPersistedEnvironmentReady(
                lastAssetsVersion = EnvironmentAssets.ASSETS_VERSION,
                setupComplete = true,
                paths = paths,
            ),
        )

        File(paths.rootfsDir, "bin/sh").apply { parentFile?.mkdirs(); writeText("stub") }
        File(paths.libDir, "libtest.so").writeText("stub")

        assertTrue(
            EnvironmentReadiness.isPersistedEnvironmentReady(
                lastAssetsVersion = EnvironmentAssets.ASSETS_VERSION,
                setupComplete = true,
                paths = paths,
            ),
        )
    }

    @Test
    fun isPersistedEnvironmentReady_reusesLegacySetupWithoutAssetsVersion() {
        val root = createTempDir("env")
        val paths = testPaths(root)

        File(paths.rootfsDir, "bin/sh").apply { parentFile?.mkdirs(); writeText("stub") }
        File(paths.libDir, "libtest.so").writeText("stub")

        assertTrue(
            EnvironmentReadiness.isPersistedEnvironmentReady(
                lastAssetsVersion = 0,
                setupComplete = true,
                paths = paths,
            ),
        )
    }

    @Test
    fun isPersistedEnvironmentReady_requiresSetupCompleteForUntrackedAssetsVersion() {
        val root = createTempDir("env")
        val paths = testPaths(root)

        File(paths.rootfsDir, "bin/sh").apply { parentFile?.mkdirs(); writeText("stub") }
        File(paths.libDir, "libtest.so").writeText("stub")

        assertFalse(
            EnvironmentReadiness.isPersistedEnvironmentReady(
                lastAssetsVersion = 0,
                setupComplete = false,
                paths = paths,
            ),
        )
    }

    @Test
    fun isQemuRuntimeReady_requiresVirtioRomAndEnUsKeymap() {
        val root = createTempDir("qemu")
        val paths = testPaths(root)
        val nativeLibDir = File(root, "native").apply { mkdirs() }

        File(nativeLibDir, "libqemu.so").writeText("stub")
        paths.uefiFirmware.parentFile?.mkdirs()
        paths.uefiFirmware.writeText("uefi")
        paths.qemuVirtioRom.parentFile?.mkdirs()
        paths.qemuVirtioRom.writeText("rom")

        assertFalse(EnvironmentReadiness.isQemuRuntimeReady(paths))

        paths.qemuEnUsKeymap.parentFile?.mkdirs()
        paths.qemuEnUsKeymap.writeText("keymap")

        assertTrue(EnvironmentReadiness.isQemuRuntimeReady(paths))
    }

    private fun testPaths(root: File): AppPaths =
        AppPaths(
            filesDir = root,
            applicationCacheDir = File(root, "cache"),
            codeCacheDir = File(root, "code_cache"),
            nativeLibraryDir = File(root, "native").absolutePath,
        )
}
