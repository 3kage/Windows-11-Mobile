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

    fun configure(showBootOverlay: Boolean) {
        _bootOverlayVisible.value = showBootOverlay
        _monitorError.value = null
    }

    fun sendAnyKeyToQemu() {
        viewModelScope.launch(Dispatchers.IO) {
            val sent = QemuMonitorClient.sendKeyWithRetries()
            withContext(Dispatchers.Main) {
                if (sent) {
                    _bootOverlayVisible.value = false
                    _monitorError.value = null
                } else {
                    _monitorError.value = "Не вдалося підключитися до QEMU monitor (127.0.0.1:4444)"
                }
            }
        }
    }
}
