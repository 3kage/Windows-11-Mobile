package com.w11mobile.core.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TermuxBinaryLocatorTest {
    @Test
    fun findBinary_prefersUsrBinPath() {
        val root = createTempDir("termux")
        try {
            val binary = File(root, "usr/bin/qemu-system-aarch64").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }

            val found = TermuxBinaryLocator.findBinary(
                searchRoots = listOf(root),
                names = listOf("qemu-system-aarch64", "qemu-system-aarch64-headless"),
            )

            assertEquals(binary.absolutePath, found?.absolutePath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findBinary_fallsBackToHeadlessAliasName() {
        val root = createTempDir("termux")
        try {
            val binary = File(root, "bin/qemu-system-aarch64-headless").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1))
            }

            val found = TermuxBinaryLocator.findBinary(
                searchRoots = listOf(root),
                names = listOf("qemu-system-aarch64", "qemu-system-aarch64-headless"),
            )

            assertEquals(binary.absolutePath, found?.absolutePath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findBinary_searchesRecursively() {
        val root = createTempDir("termux")
        try {
            val binary = File(root, "nested/libexec/qemu-system-x86_64").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(9))
            }

            val found = TermuxBinaryLocator.findBinary(
                searchRoots = listOf(root),
                names = listOf("qemu-system-x86_64"),
            )

            assertEquals(binary.absolutePath, found?.absolutePath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun describeSearchRoots_listsTopLevelEntries() {
        val root = createTempDir("termux")
        try {
            File(root, "usr/bin/qemu-img").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1))
            }

            val description = TermuxBinaryLocator.describeSearchRoots(listOf(root))

            assertTrue(description.contains("usr/bin/: qemu-img"))
            assertTrue(description.contains("top-level:"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findBinary_ignoresEmptyFiles() {
        val root = createTempDir("termux")
        try {
            File(root, "bin/qemu-system-aarch64").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf())
            }

            assertNull(
                TermuxBinaryLocator.findBinary(
                    searchRoots = listOf(root),
                    names = listOf("qemu-system-aarch64"),
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findBinary_returnsFirstMatchingNameInOrder() {
        val root = createTempDir("termux")
        try {
            val preferred = File(root, "bin/qemu-system-aarch64").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            File(root, "bin/qemu-system-aarch64-headless").apply {
                writeBytes(byteArrayOf(2))
            }

            val found = TermuxBinaryLocator.findBinary(
                searchRoots = listOf(root),
                names = listOf("qemu-system-aarch64", "qemu-system-aarch64-headless"),
            )

            assertNotNull(found)
            assertEquals(preferred.absolutePath, found?.absolutePath)
        } finally {
            root.deleteRecursively()
        }
    }
}
