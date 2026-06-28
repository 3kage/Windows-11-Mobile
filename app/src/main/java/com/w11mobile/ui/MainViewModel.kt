package com.w11mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.w11mobile.core.environment.EnvironmentSetupOrchestrator
import com.w11mobile.core.environment.SetupPreferences
import com.w11mobile.core.environment.SetupStep
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SetupPreferences(application)
    private val orchestrator = EnvironmentSetupOrchestrator(
        application = application,
        preferences = preferences,
        onStepChanged = { step -> updateState { copy(step = step, stepLabel = step.labelUk, errorMessage = null) } },
        onProgressChanged = { progress, indeterminate ->
            updateState { copy(progress = progress, isIndeterminate = indeterminate) }
        },
        onLog = { text -> appendLog(text) },
    )

    private val _uiState = MutableLiveData(
        SetupUiState(
            windowsImageUrl = preferences.windowsImageUrl,
            environmentReady = preferences.setupComplete || orchestrator.isEnvironmentReady(),
            canLaunchWindows = orchestrator.canLaunchWindows(),
        ),
    )
    val uiState: LiveData<SetupUiState> = _uiState

    fun setWindowsImageUrl(url: String) {
        updateState { copy(windowsImageUrl = url) }
    }

    fun initializeWindows11() {
        val state = _uiState.value ?: return
        if (state.isRunning) return

        viewModelScope.launch {
            updateState {
                copy(
                    isRunning = true,
                    environmentReady = false,
                    canLaunchWindows = false,
                    errorMessage = null,
                    step = SetupStep.VERIFY_DEVICE,
                    stepLabel = SetupStep.VERIFY_DEVICE.labelUk,
                    progress = 0,
                    isIndeterminate = false,
                )
            }
            appendLog(">>> Повна ініціалізація Windows 11 середовища...\n")

            try {
                orchestrator.runFullSetup(state.windowsImageUrl.trim())
                updateState {
                    copy(
                        isRunning = false,
                        environmentReady = orchestrator.isEnvironmentReady(),
                        canLaunchWindows = orchestrator.canLaunchWindows(),
                        step = SetupStep.COMPLETE,
                        stepLabel = SetupStep.COMPLETE.labelUk,
                        progress = 100,
                        isIndeterminate = false,
                    )
                }
            } catch (error: Exception) {
                updateState {
                    copy(
                        isRunning = false,
                        environmentReady = orchestrator.isEnvironmentReady(),
                        canLaunchWindows = orchestrator.canLaunchWindows(),
                        step = SetupStep.ERROR,
                        stepLabel = SetupStep.ERROR.labelUk,
                        errorMessage = error.message,
                        isIndeterminate = false,
                    )
                }
            }
        }
    }

    fun launchWindows11() {
        val state = _uiState.value ?: return
        if (state.isRunning) return

        viewModelScope.launch {
            updateState { copy(isRunning = true, errorMessage = null) }
            appendLog("\n>>> Запуск Windows 11...\n")
            try {
                orchestrator.launchWindows()
            } catch (error: Exception) {
                appendLog("\n[ПОМИЛКА] ${error.message}\n")
                updateState { copy(errorMessage = error.message) }
            } finally {
                updateState {
                    copy(
                        isRunning = false,
                        environmentReady = orchestrator.isEnvironmentReady(),
                        canLaunchWindows = orchestrator.canLaunchWindows(),
                    )
                }
            }
        }
    }

    fun clearLog() {
        updateState { copy(terminalLog = "") }
    }

    private fun appendLog(text: String) {
        updateState { copy(terminalLog = terminalLog + text) }
    }

    private fun updateState(transform: SetupUiState.() -> SetupUiState) {
        _uiState.value = (_uiState.value ?: SetupUiState()).transform()
    }
}
