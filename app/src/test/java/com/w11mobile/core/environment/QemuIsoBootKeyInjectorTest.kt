package com.w11mobile.core.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QemuIsoBootKeyInjectorTest {
    private val injector = QemuIsoBootKeyInjector()

    @Test
    fun looksLikePressAnyKeyPrompt_detectsPlainText() {
        assertTrue(injector.looksLikePressAnyKeyPrompt("Press any key to boot from CD or DVD......"))
    }

    @Test
    fun looksLikePressAnyKeyPrompt_detectsAnsiWrappedText() {
        val line = "\u001B[2J\u001B[01;01HPress any key to boot from CD or DVD......"
        assertTrue(injector.looksLikePressAnyKeyPrompt(line))
    }

    @Test
    fun looksLikePressAnyKeyPrompt_ignoresUnrelatedLines() {
        assertFalse(injector.looksLikePressAnyKeyPrompt("UEFI Interactive Shell v2.2"))
    }
}
