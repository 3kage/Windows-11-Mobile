package com.w11mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.w11mobile.core.ShellExecutor
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val shellExecutor = ShellExecutor(
        workingDirectory = application.filesDir,
        environment = mapOf(
            "PATH" to "/system/bin:/system/xbin:/vendor/bin",
            "HOME" to application.filesDir.absolutePath,
            "TERM" to "xterm-256color",
        ),
    )

    private val _terminalLog = MutableLiveData("")
    val terminalLog: LiveData<String> = _terminalLog

    private val _isInitializing = MutableLiveData(false)
    val isInitializing: LiveData<Boolean> = _isInitializing

    fun initializeWindows11() {
        if (_isInitializing.value == true) return

        viewModelScope.launch {
            _isInitializing.value = true
            appendLog(">>> Ініціалізація Windows 11 середовища...\n")

            runCommand("uname", "-a")
            runCommand("id")
            runScript(
                """
                echo "Перевірка user-space середовища..."
                echo "PWD: $(pwd)"
                echo "Готово до розгортання PRoot/Termux образу."
                """.trimIndent()
            )

            appendLog("\n>>> Ініціалізація завершена.\n")
            _isInitializing.value = false
        }
    }

    fun clearLog() {
        _terminalLog.value = ""
    }

    private suspend fun runCommand(command: String, vararg args: String) {
        appendLog("$ ${listOf(command, *args).joinToString(" ")}\n")
        val result = shellExecutor.execute(command, *args)
        appendLog(result.combinedOutput())
        appendLog("\n[exit ${result.exitCode}]\n\n")
    }

    private suspend fun runScript(script: String) {
        appendLog("$ sh -c \"...\"\n")
        val result = shellExecutor.executeScript(script)
        appendLog(result.combinedOutput())
        appendLog("\n[exit ${result.exitCode}]\n\n")
    }

    private fun appendLog(text: String) {
        _terminalLog.value = (_terminalLog.value.orEmpty()) + text
    }
}
