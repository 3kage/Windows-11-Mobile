package com.w11mobile.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.w11mobile.core.environment.QemuNativeLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

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
            try {
                Socket(QemuNativeLauncher.MONITOR_HOST, QemuNativeLauncher.MONITOR_PORT).use { socket ->
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println("sendkey spc")
                }
                withContext(Dispatchers.Main) {
                    _bootOverlayVisible.value = false
                    _monitorError.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _monitorError.value = e.message ?: "QEMU monitor error"
                }
            }
        }
    }
}
