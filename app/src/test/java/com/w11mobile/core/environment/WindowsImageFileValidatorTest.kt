package com.w11mobile.core.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsImageFileValidatorTest {

    @Test
    fun acceptsIsoAndQcow2() {
        assertTrue(WindowsImageFileValidator.isSupportedFileName("Win11.iso"))
        assertTrue(WindowsImageFileValidator.isSupportedFileName("disk.QCOW2"))
        assertNull(WindowsImageFileValidator.validateFileName("Win11.iso"))
    }

    @Test
    fun rejectsApkWithSpecificMessage() {
        assertFalse(WindowsImageFileValidator.isSupportedFileName("windows11-mobile-2.0.3-release.apk"))
        val message = WindowsImageFileValidator.validateFileName("windows11-mobile-2.0.3-release.apk")
        assertNotNull(message)
        assertTrue(message!!.contains(".apk"))
        assertTrue(message.contains(".iso"))
    }

    @Test
    fun rejectsUnknownExtension() {
        val message = WindowsImageFileValidator.validateFileName("document.pdf")
        assertNotNull(message)
        assertTrue(message!!.contains(".pdf"))
    }
}
