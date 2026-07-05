package com.w11mobile.ui

import android.app.Activity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import com.w11mobile.core.environment.QemuMonitorClient
import com.w11mobile.core.environment.QemuMonitorKeyMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object QemuKeyboardInputHelper {

    fun bindEditText(
        editText: EditText,
        scope: CoroutineScope,
        onMonitorError: ((String) -> Unit)? = null,
    ) {
        var forwarding = false

        editText.doAfterTextChanged { editable ->
            if (forwarding) return@doAfterTextChanged
            val text = editable?.toString().orEmpty()
            if (text.isEmpty()) return@doAfterTextChanged

            forwarding = true
            editText.text?.clear()
            forwarding = false

            text.forEach { ch ->
                sendMonitorKey(scope, QemuMonitorKeyMapper.mapChar(ch), onMonitorError)
            }
        }

        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }
            val key = QemuMonitorKeyMapper.mapKeyCode(keyCode)
            if (key != null) {
                sendMonitorKey(scope, key, onMonitorError)
                true
            } else {
                false
            }
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_NULL
            ) {
                sendMonitorKey(scope, "ret", onMonitorError)
                true
            } else {
                false
            }
        }
    }

    fun showKeyboard(activity: Activity, editText: EditText) {
        editText.requestFocus()
        val imm = activity.getSystemService(InputMethodManager::class.java)
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard(activity: Activity, editText: EditText) {
        val imm = activity.getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    fun sendSpace(scope: CoroutineScope, onMonitorError: ((String) -> Unit)? = null) {
        sendMonitorKey(scope, "spc", onMonitorError)
    }

    private fun sendMonitorKey(
        scope: CoroutineScope,
        key: String?,
        onMonitorError: ((String) -> Unit)?,
    ) {
        if (key.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            val sent = QemuMonitorClient.sendMonitorCommand(
                command = "sendkey $key",
                waitForPortMs = 5_000L,
            )
            if (!sent) {
                onMonitorError?.invoke("Не вдалося підключитися до QEMU monitor (127.0.0.1:4444)")
            }
        }
    }
}
