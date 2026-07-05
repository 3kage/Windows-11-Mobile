package com.w11mobile.core.environment

import android.view.KeyEvent

/**
 * Maps Android text input and key events to QEMU HMP `sendkey` key names.
 *
 * @see <a href="https://www.qemu.org/docs/master/system/monitor.html">QEMU monitor</a>
 */
object QemuMonitorKeyMapper {

    fun mapChar(ch: Char): String? =
        when (ch) {
            in 'a'..'z' -> ch.toString()
            in 'A'..'Z' -> "shift-${ch.lowercaseChar()}"
            in '0'..'9' -> ch.toString()
            ' ' -> "spc"
            '\n', '\r' -> "ret"
            '\t' -> "tab"
            else -> SHIFTED_CHARS[ch] ?: PLAIN_CHARS[ch]
        }

    fun mapKeyCode(keyCode: Int): String? =
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> "ret"
            KeyEvent.KEYCODE_DEL -> "backspace"
            KeyEvent.KEYCODE_FORWARD_DEL -> "delete"
            KeyEvent.KEYCODE_TAB -> "tab"
            KeyEvent.KEYCODE_ESCAPE -> "esc"
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            KeyEvent.KEYCODE_MOVE_HOME -> "home"
            KeyEvent.KEYCODE_MOVE_END -> "end"
            KeyEvent.KEYCODE_PAGE_UP -> "pgup"
            KeyEvent.KEYCODE_PAGE_DOWN -> "pgdn"
            else -> null
        }

    private val PLAIN_CHARS = mapOf(
        '-' to "minus",
        '=' to "equal",
        '[' to "bracket_left",
        ']' to "bracket_right",
        '\\' to "backslash",
        ';' to "semicolon",
        '\'' to "apostrophe",
        ',' to "comma",
        '.' to "dot",
        '/' to "slash",
        '`' to "grave_accent",
    )

    private val SHIFTED_CHARS = mapOf(
        '!' to "shift-1",
        '@' to "shift-2",
        '#' to "shift-3",
        '$' to "shift-4",
        '%' to "shift-5",
        '^' to "shift-6",
        '&' to "shift-7",
        '*' to "shift-8",
        '(' to "shift-9",
        ')' to "shift-0",
        '_' to "shift-minus",
        '+' to "shift-equal",
        '{' to "shift-bracket_left",
        '}' to "shift-bracket_right",
        '|' to "shift-backslash",
        ':' to "shift-semicolon",
        '"' to "shift-apostrophe",
        '<' to "shift-comma",
        '>' to "shift-dot",
        '?' to "shift-slash",
        '~' to "shift-grave_accent",
    )
}
