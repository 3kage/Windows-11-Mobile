package com.w11mobile.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
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
    private val viewModel: WindowsDisplayViewModel by viewModels()
    private var vncClient: MinimalVncClient? = null
    private var connectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWindowsDisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val showBootOverlay = intent.getBooleanExtra(EXTRA_SHOW_BOOT_OVERLAY, false)
        viewModel.configure(showBootOverlay)

        binding.vncFrameView.onPointerEvent = { frameX, frameY, pressed ->
            vncClient?.sendPointer(frameX, frameY, pressed)
        }

        binding.bootOverlay.setOnClickListener {
            viewModel.sendAnyKeyToQemu()
        }

        binding.btnSendAnyKey.setOnClickListener {
            viewModel.sendAnyKeyToQemu()
        }

        binding.btnCloseDisplay.setOnClickListener {
            finish()
        }

        viewModel.bootOverlayVisible.observe(this) { visible ->
            binding.bootOverlay.isVisible = visible
            binding.displayControls.isVisible = !visible
        }

        viewModel.monitorError.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                binding.vncStatusText.text = getString(
                    com.w11mobile.R.string.windows_display_monitor_error,
                    error,
                )
            }
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
                                        if (viewModel.bootOverlayVisible.value != true) {
                                            binding.vncStatusText.text =
                                                getString(com.w11mobile.R.string.windows_display_connected)
                                        }
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
        const val EXTRA_SHOW_BOOT_OVERLAY = "show_boot_overlay"

        private const val MAX_CONNECT_ATTEMPTS = 45
        private const val RETRY_DELAY_MS = 1_000L
    }
}
