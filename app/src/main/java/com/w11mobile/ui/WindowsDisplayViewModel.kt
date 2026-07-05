package com.w11mobile.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.w11mobile.core.environment.QemuMonitorClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WindowsDisplayViewModel : ViewModel() {

    private val _bootOverlayVisible = MutableLiveData(false)
    val bootOverlayVisible: LiveData<Boolean> = _bootOverlayVisible

    private val _monitorError = MutableLiveData<String?>(null)
    val monitorError: LiveData<String?> = _monitorError

    private val _vncConnected = MutableLiveData(false)
    val vncConnected: LiveData<Boolean> = _vncConnected

    private val _qemuFatalError = MutableLiveData<String?>(null)
    val qemuFatalError: LiveData<String?> = _qemuFatalError

    fun configure(showBootOverlay: Boolean) {
        _bootOverlayVisible.value = showBootOverlay
        _monitorError.value = null
        _vncConnected.value = false
        _qemuFatalError.value = null
    }

    fun onVncConnected() {
        _vncConnected.value = true
    }

    fun onQemuProcessDied(exitCode: Int) {
        _qemuFatalError.value = "Помилка: Процес QEMU несподівано завершився з кодом $exitCode"
    }

    fun sendAnyKeyToQemu() {
        sendMonitorKey("spc", dismissBootOverlayOnSuccess = true)
    }

    fun sendMonitorKey(key: String, dismissBootOverlayOnSuccess: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val sent = QemuMonitorClient.sendRawMonitorCommand("sendkey $key")
            withContext(Dispatchers.Main) {
                if (sent) {
                    _monitorError.value = null
                    if (dismissBootOverlayOnSuccess && _vncConnected.value == true) {
                        _bootOverlayVisible.value = false
                    }
                } else {
                    _monitorError.value = "Не вдалося підключитися до QEMU monitor (127.0.0.1:4444)"
                }
            }
        }
    }
}
