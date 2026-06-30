package com.w11mobile.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.w11mobile.core.environment.QemuMonitorClient
import com.w11mobile.databinding.ActivityWindowsDisplayBinding
import com.w11mobile.vnc.MinimalVncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WindowsDisplayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWindowsDisplayBinding
    private var vncClient: MinimalVncClient? = null
    private var connectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWindowsDisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.vncFrameView.onPointerEvent = { frameX, frameY, pressed ->
            vncClient?.sendPointer(frameX, frameY, pressed)
        }

        binding.btnSendAnyKey.setOnClickListener {
            vncClient?.sendAnyKey()
            QemuMonitorClient.sendKey()
        }

        binding.btnCloseDisplay.setOnClickListener {
            finish()
        }

        startVncConnection()
    }

    override fun onDestroy() {
        connectJob?.cancel()
        vncClient?.close()
        vncClient = null
        super.onDestroy()
    }

    private fun startVncConnection() {
        connectJob = lifecycleScope.launch {
            var attempt = 0
            while (isActive && attempt < MAX_CONNECT_ATTEMPTS) {
                attempt += 1
                val client = MinimalVncClient()
                vncClient = client
                binding.vncStatusText.text = getString(
                    com.w11mobile.R.string.windows_display_connecting_attempt,
                    attempt,
                )
                try {
                    withContext(Dispatchers.IO) {
                        client.connect(
                            object : MinimalVncClient.FrameListener {
                                override fun onStatus(message: String) {
                                    runOnUiThread {
                                        binding.vncStatusText.text = message
                                    }
                                }

                                override fun onFrame(bitmap: android.graphics.Bitmap) {
                                    runOnUiThread {
                                        binding.vncFrameView.updateFrame(bitmap)
                                        binding.vncStatusText.text =
                                            getString(com.w11mobile.R.string.windows_display_connected)
                                    }
                                }

                                override fun onDisconnected(error: String?) {
                                    runOnUiThread {
                                        binding.vncStatusText.text = error
                                            ?: getString(com.w11mobile.R.string.windows_display_disconnected)
                                    }
                                }
                            },
                        )
                    }
                    return@launch
                } catch (_: Exception) {
                    client.close()
                    if (vncClient === client) {
                        vncClient = null
                    }
                    delay(RETRY_DELAY_MS)
                }
            }
            binding.vncStatusText.text = getString(com.w11mobile.R.string.windows_display_connect_failed)
        }
    }

    companion object {
        private const val MAX_CONNECT_ATTEMPTS = 45
        private const val RETRY_DELAY_MS = 1_000L
    }
}
