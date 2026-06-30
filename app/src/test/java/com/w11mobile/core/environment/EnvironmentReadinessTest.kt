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

    private fun testPaths(root: File): AppPaths =
        AppPaths(
            filesDir = root,
            applicationCacheDir = File(root, "cache"),
            codeCacheDir = File(root, "code_cache"),
            nativeLibraryDir = File(root, "native").absolutePath,
        )
}
