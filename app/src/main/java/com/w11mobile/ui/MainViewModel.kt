package com.w11mobile.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.w11mobile.core.environment.EnvironmentSetupOrchestrator
import com.w11mobile.core.environment.ImageSource
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
            imageSource = preferences.imageSource,
            windowsImageUrl = preferences.windowsImageUrl,
            localImageUri = preferences.localImageUri,
            localImageName = preferences.localImageName,
            environmentReady = preferences.setupComplete || orchestrator.isEnvironmentReady(),
            canLaunchWindows = orchestrator.canLaunchWindows(),
        ),
    )
    val uiState: LiveData<SetupUiState> = _uiState

    fun setImageSource(source: ImageSource) {
        preferences.imageSource = source
        updateState { copy(imageSource = source) }
    }

    fun setWindowsImageUrl(url: String) {
        preferences.windowsImageUrl = url
        updateState { copy(windowsImageUrl = url) }
    }

    fun onLocalImageSelected(uri: Uri, displayName: String?) {
        val uriString = uri.toString()
        preferences.localImageUri = uriString
        preferences.localImageName = displayName
        preferences.imageSource = ImageSource.LOCAL
        updateState {
            copy(
                imageSource = ImageSource.LOCAL,
                localImageUri = uriString,
                localImageName = displayName,
                errorMessage = null,
            )
        }
        appendLog("\n>>> Обрано локальний файл: ${displayName ?: uriString}\n")
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
                orchestrator.runFullSetup(
                    imageSource = state.imageSource,
                    windowsImageUrl = state.windowsImageUrl.trim(),
                    localImageUri = state.localImageUri,
                    localImageName = state.localImageName,
                )
                refreshEnvironmentState(SetupStep.COMPLETE, SetupStep.COMPLETE.labelUk, 100)
            } catch (error: Exception) {
                refreshEnvironmentState(
                    SetupStep.ERROR,
                    SetupStep.ERROR.labelUk,
                    _uiState.value?.progress ?: 0,
                    error.message,
                )
            }
        }
    }

    fun importLocalImageOnly() {
        val state = _uiState.value ?: return
        if (state.isRunning) return
        val uri = state.localImageUri ?: run {
            updateState { copy(errorMessage = "Спочатку оберіть локальний файл образу.") }
            return
        }

        viewModelScope.launch {
            updateState { copy(isRunning = true, errorMessage = null, isIndeterminate = true) }
            try {
                if (orchestrator.isEnvironmentReady()) {
                    orchestrator.importLocalImageOnly(uri, state.localImageName)
                } else {
                    appendLog(">>> Середовище ще не готове — запускаємо повну ініціалізацію...\n")
                    orchestrator.runFullSetup(
                        imageSource = ImageSource.LOCAL,
                        windowsImageUrl = "",
                        localImageUri = uri,
                        localImageName = state.localImageName,
                    )
                }
                refreshEnvironmentState(SetupStep.COMPLETE, "Образ імпортовано", 100)
            } catch (error: Exception) {
                refreshEnvironmentState(SetupStep.ERROR, SetupStep.ERROR.labelUk, _uiState.value?.progress ?: 0, error.message)
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

    private fun refreshEnvironmentState(
        step: SetupStep,
        stepLabel: String,
        progress: Int,
        errorMessage: String? = null,
    ) {
        updateState {
            copy(
                isRunning = false,
                environmentReady = orchestrator.isEnvironmentReady(),
                canLaunchWindows = orchestrator.canLaunchWindows(),
                step = step,
                stepLabel = stepLabel,
                progress = progress,
                isIndeterminate = false,
                errorMessage = errorMessage,
            )
        }
    }

    private fun appendLog(text: String) {
        updateState { copy(terminalLog = terminalLog + text) }
    }

    private fun updateState(transform: SetupUiState.() -> SetupUiState) {
        _uiState.value = (_uiState.value ?: SetupUiState()).transform()
    }
}
