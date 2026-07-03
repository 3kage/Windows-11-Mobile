package com.w11mobile.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.w11mobile.core.environment.QemuProcessSession
import com.w11mobile.core.environment.QemuRuntimeEvents
import com.w11mobile.databinding.ActivityWindowsDisplayBinding
import com.w11mobile.vnc.MinimalVncClient
import com.w11mobile.vnc.VncConnectionDiagnostics
import com.w11mobile.vnc.VncPortProbe
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.configure(intent.getBooleanExtra(EXTRA_SHOW_BOOT_OVERLAY, false))
    }

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

        viewModel.qemuFatalError.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                binding.vncStatusText.text = message
                finish()
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
                binding.vncStatusText.text = getString(
                    com.w11mobile.R.string.windows_display_connecting_attempt,
                    attempt,
                )

                val exitCode = withContext(Dispatchers.IO) { QemuProcessSession.resolvedExitCodeOrNull() }
                if (exitCode != null) {
                    handleQemuProcessDeath(exitCode)
                    return@launch
                }

                val probe = withContext(Dispatchers.IO) { VncPortProbe.probe() }
                if (!probe.open) {
                    if (attempt == 1 || attempt % 5 == 0) {
                        QemuRuntimeEvents.publishStatus(
                            "VNC 127.0.0.1:5900 ще не відкритий (спроба $attempt/$MAX_CONNECT_ATTEMPTS). " +
                                "UEFI-текст у логу — це не екран Windows; чекайте сенсорний дисплей.",
                        )
                    }
                    delay(RETRY_DELAY_MS)
                    continue
                }

                QemuRuntimeEvents.publishStatus("VNC порт 5900 відкритий, підключення RFB…")

                val client = MinimalVncClient()
                vncClient = client
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
                                        val firstFrame = viewModel.vncConnected.value != true
                                        viewModel.onVncConnected()
                                        if (firstFrame) {
                                            QemuRuntimeEvents.publishStatus(
                                                "VNC підключено ${bitmap.width}x${bitmap.height} — зображення Windows на сенсорному екрані",
                                            )
                                        }
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
                } catch (error: Exception) {
                    VncConnectionDiagnostics.logSocketFailure(
                        stage = "RFB session attempt $attempt",
                        host = probe.host,
                        port = probe.port,
                        error = error,
                    )
                    client.close()
                    if (vncClient === client) {
                        vncClient = null
                    }

                    val postConnectExit = QemuProcessSession.resolvedExitCodeOrNull()
                    if (postConnectExit != null) {
                        handleQemuProcessDeath(postConnectExit)
                        return@launch
                    }
                    delay(RETRY_DELAY_MS)
                }
            }
            binding.vncStatusText.text = getString(com.w11mobile.R.string.windows_display_connect_failed)
        }
    }

    private fun handleQemuProcessDeath(exitCode: Int) {
        connectJob?.cancel()
        val message = getString(com.w11mobile.R.string.windows_display_qemu_died, exitCode)
        Log.e(TAG, message)
        QemuRuntimeEvents.publishFatal(message)
        viewModel.onQemuProcessDied(exitCode)
    }

    companion object {
        private const val TAG = "WindowsDisplay"

        const val EXTRA_SHOW_BOOT_OVERLAY = "show_boot_overlay"

        private const val MAX_CONNECT_ATTEMPTS = 45
        private const val RETRY_DELAY_MS = 1_000L
    }
}
