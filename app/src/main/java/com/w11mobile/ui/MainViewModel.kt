package com.w11mobile.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.w11mobile.core.environment.QemuBoot0001AutoKey
import com.w11mobile.core.environment.QemuEfiShellAutoKey
import com.w11mobile.core.environment.EnvironmentSetupOrchestrator
import com.w11mobile.core.environment.ImageSource
import com.w11mobile.core.environment.QemuRuntimeEvents
import com.w11mobile.core.environment.SetupPreferences
import com.w11mobile.core.environment.SetupStep
import com.w11mobile.core.environment.WindowsImageArch
import com.w11mobile.service.QemuService
import com.w11mobile.service.QemuServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableLiveData(SetupUiState())
    val uiState: LiveData<SetupUiState> = _uiState

    private val uiStateStore = AtomicReference(_uiState.value ?: SetupUiState())
    private val preferences = SetupPreferences(application)
    private val orchestrator = EnvironmentSetupOrchestrator(
        application = application,
        preferences = preferences,
        onStepChanged = { step ->
            updateState { copy(step = step, stepLabel = step.labelUk, errorMessage = null) }
        },
        onProgressChanged = { progress, indeterminate ->
            updateState { copy(progress = progress, isIndeterminate = indeterminate) }
        },
        onLog = { text -> appendLog(text) },
    )

    init {
        QemuRuntimeEvents.onFatalError = { message ->
            appendLog("\n>>> $message\n")
            updateState {
                copy(
                    errorMessage = message,
                    windowsSessionActive = false,
                    isRunning = false,
                )
            }
        }
        QemuRuntimeEvents.onStatus = { message ->
            appendLog(">>> $message\n")
        }
        QemuRuntimeEvents.onTerminalLine = { line ->
            appendLog(line)
        }
        QemuRuntimeEvents.onSessionEnded = { exitCode ->
            viewModelScope.launch {
                if (exitCode != 0) {
                    appendLog("\n>>> QEMU завершився з кодом $exitCode\n")
                } else {
                    appendLog("\n>>> Сесію Windows завершено.\n")
                }
                val flags = withContext(Dispatchers.IO) { readEnvironmentFlags() }
                updateState {
                    copy(
                        isRunning = false,
                        windowsSessionActive = false,
                        environmentReady = flags.first,
                        canLaunchWindows = flags.second,
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val initialState = loadInitialUiState()
            uiStateStore.set(initialState)
            _uiState.postValue(initialState)
        }
    }

    fun onQemuServiceConnected(service: QemuService) {
        if (service.isQemuAlive() || service.isLaunchInProgress()) {
            updateState {
                copy(
                    windowsSessionActive = true,
                    isRunning = service.isLaunchInProgress(),
                )
            }
        }
    }

    fun onQemuServiceDisconnected() {
        // Service process may still be running; session state comes from QemuProcessSession events.
    }

    fun setImageSource(source: ImageSource) {
        preferences.imageSource = source
        updateState { copy(imageSource = source) }
    }

    fun setWindowsImageUrl(url: String) {
        preferences.windowsImageUrl = url
        updateState { copy(windowsImageUrl = url) }
    }

    fun setWindowsImageArch(arch: WindowsImageArch) {
        preferences.windowsImageArch = arch
        updateState { copy(windowsImageArch = arch) }
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
        if (displayName?.contains("arm", ignoreCase = true) == true) {
            appendLog(">>> Виявлено ARM64 ISO — рекомендуємо режим ARM64.\n")
        }
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
                    imageArch = state.windowsImageArch,
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
                if (withContext(Dispatchers.IO) { orchestrator.refreshEnvironmentReadinessFromDisk() }) {
                    orchestrator.importLocalImageOnly(uri, state.localImageName, state.windowsImageArch)
                } else {
                    appendLog(">>> Середовище ще не готове — запускаємо повну ініціалізацію...\n")
                    orchestrator.runFullSetup(
                        imageSource = ImageSource.LOCAL,
                        windowsImageUrl = "",
                        localImageUri = uri,
                        localImageName = state.localImageName,
                        imageArch = state.windowsImageArch,
                    )
                }
                refreshEnvironmentState(SetupStep.COMPLETE, "Образ імпортовано", 100)
            } catch (error: Exception) {
                refreshEnvironmentState(SetupStep.ERROR, SetupStep.ERROR.labelUk, _uiState.value?.progress ?: 0, error.message)
            }
        }
    }

    suspend fun prepareWindowsLaunch(): Boolean? = withContext(Dispatchers.IO) {
        try {
            appendLog("\n>>> Запуск Windows 11...\n")
            if (!orchestrator.refreshEnvironmentReadinessFromDisk()) {
                error("Середовище не готове. Завершіть ініціалізацію один раз — повторне розпакування не потрібне.")
            }
            if (!orchestrator.canLaunchWindows()) {
                error("Образ Windows не знайдено. Імпортуйте ISO або QCOW2.")
            }
            orchestrator.isIsoBootMode()
        } catch (error: Exception) {
            appendLog("\n[ПОМИЛКА] ${error.message}\n")
            updateState { copy(errorMessage = error.message) }
            null
        }
    }

    fun runWindowsLaunch(isoBootMode: Boolean) {
        QemuBoot0001AutoKey.reset()
        QemuEfiShellAutoKey.reset()
        updateState {
            copy(
                isRunning = true,
                windowsSessionActive = true,
                isoBootMode = isoBootMode,
                errorMessage = null,
            )
        }
        if (isoBootMode) {
            appendLog(">>> Торкніться «Будь-яка клавіша» на сенсорному екрані або в головному меню.\n")
        } else {
            appendLog(">>> Керуйте Windows через сенсорний екран.\n")
        }
        appendLog(">>> QEMU запускається у Foreground Service (стійкий фоновий режим)…\n")
        QemuServiceController.startLaunch(getApplication(), isoBootMode)
    }

    fun sendAnyKeyToQemu() {
        viewModelScope.launch(Dispatchers.IO) {
            val sent = com.w11mobile.core.environment.QemuMonitorClient.sendMonitorCommand(
                command = "sendkey spc",
                waitForPortMs = 15_000L,
            )
            withContext(Dispatchers.Main) {
                if (sent) {
                    appendLog(">>> Пробіл надіслано в QEMU monitor (127.0.0.1:4444).\n")
                } else {
                    appendMonitorError("Не вдалося надіслати клавішу. Переконайтеся, що Windows запущено.")
                }
            }
        }
    }

    fun appendMonitorError(message: String) {
        appendLog(">>> $message\n")
        updateState { copy(errorMessage = message) }
    }

    fun isIsoBootModeCached(): Boolean = _uiState.value?.isoBootMode == true

    fun isWindowsSessionActive(): Boolean = _uiState.value?.windowsSessionActive == true

    override fun onCleared() {
        QemuRuntimeEvents.onFatalError = null
        QemuRuntimeEvents.onStatus = null
        QemuRuntimeEvents.onTerminalLine = null
        QemuRuntimeEvents.onSessionEnded = null
        super.onCleared()
    }

    fun clearLog() {
        updateState { copy(terminalLog = "") }
    }

    private suspend fun loadInitialUiState(): SetupUiState = withContext(Dispatchers.IO) {
        SetupUiState(
            imageSource = preferences.imageSource,
            windowsImageUrl = preferences.windowsImageUrl,
            localImageUri = preferences.localImageUri,
            localImageName = preferences.localImageName,
            windowsImageArch = preferences.windowsImageArch,
            environmentReady = orchestrator.isEnvironmentReady(),
            canLaunchWindows = orchestrator.canLaunchWindows(),
            windowsSessionActive = QemuServiceController.isQemuRunning(),
        )
    }

    private suspend fun refreshEnvironmentState(
        step: SetupStep,
        stepLabel: String,
        progress: Int,
        errorMessage: String? = null,
    ) {
        val flags = withContext(Dispatchers.IO) { readEnvironmentFlags() }
        updateState {
            copy(
                isRunning = false,
                environmentReady = flags.first,
                canLaunchWindows = flags.second,
                step = step,
                stepLabel = stepLabel,
                progress = progress,
                isIndeterminate = false,
                errorMessage = errorMessage,
            )
        }
    }

    private fun readEnvironmentFlags(): Pair<Boolean, Boolean> =
        orchestrator.isEnvironmentReady() to orchestrator.canLaunchWindows()

    private fun appendLog(text: String) {
        updateState { copy(terminalLog = terminalLog + text) }
        QemuBoot0001AutoKey.onTerminalLine(text, viewModelScope)
        QemuEfiShellAutoKey.onTerminalLine(text, viewModelScope)
    }

    private fun updateState(transform: SetupUiState.() -> SetupUiState) {
        val newState = uiStateStore.updateAndGet { current ->
            (current ?: _uiState.value ?: SetupUiState()).transform()
        }
        _uiState.postValue(newState)
    }
}
