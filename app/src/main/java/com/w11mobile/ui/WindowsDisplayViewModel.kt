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

    fun configure(showBootOverlay: Boolean) {
        _bootOverlayVisible.value = showBootOverlay
        _monitorError.value = null
        _vncConnected.value = false
    }

    fun onVncConnected() {
        _vncConnected.value = true
    }

    fun sendAnyKeyToQemu() {
        viewModelScope.launch(Dispatchers.IO) {
            val sent = QemuMonitorClient.sendKeyWithRetries()
            withContext(Dispatchers.Main) {
                if (sent) {
                    _monitorError.value = null
                    if (_vncConnected.value == true) {
                        _bootOverlayVisible.value = false
                    }
                } else {
                    _monitorError.value = "Не вдалося підключитися до QEMU monitor (127.0.0.1:4444)"
                }
            }
        }
    }
}
