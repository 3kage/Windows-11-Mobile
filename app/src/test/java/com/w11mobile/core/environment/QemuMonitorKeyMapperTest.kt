package com.w11mobile.core.environment

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QemuMonitorKeyMapperTest {

    @Test
    fun mapChar_lowercaseLetter() {
        assertEquals("a", QemuMonitorKeyMapper.mapChar('a'))
    }

    @Test
    fun mapChar_uppercaseLetter() {
        assertEquals("shift-a", QemuMonitorKeyMapper.mapChar('A'))
    }

    @Test
    fun mapChar_colonUsesShiftSemicolon() {
        assertEquals("shift-semicolon", QemuMonitorKeyMapper.mapChar(':'))
    }

    @Test
    fun mapChar_space() {
        assertEquals("spc", QemuMonitorKeyMapper.mapChar(' '))
    }

    @Test
    fun mapKeyCode_enter() {
        assertEquals("ret", QemuMonitorKeyMapper.mapKeyCode(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun mapKeyCode_unknownReturnsNull() {
        assertNull(QemuMonitorKeyMapper.mapKeyCode(KeyEvent.KEYCODE_CAMERA))
    }
}
