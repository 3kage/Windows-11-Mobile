package com.w11mobile.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.w11mobile.databinding.ActivityWindowsDisplayBinding
import com.w11mobile.core.environment.QemuProcessSession
import com.w11mobile.core.environment.QemuRuntimeEvents
import com.w11mobile.service.QemuServiceController
import com.w11mobile.vnc.MinimalVncClient
import com.w11mobile.vnc.VncConnectionDiagnostics
import com.w11mobile.vnc.VncEndpoint
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

    private val qemuServiceConnection = QemuServiceController.createConnection()

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
        binding.vncFrameView.isFocusable = false
        binding.vncFrameView.isFocusableInTouchMode = false

        setupInputControls()

        viewModel.bootOverlayVisible.observe(this) { visible ->
            binding.bootOverlay.isVisible = visible
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
    }

    override fun onStart() {
        super.onStart()
        QemuServiceController.bind(this, qemuServiceConnection)
    }

    override fun onStop() {
        pauseVncConnection()
        QemuServiceController.unbind(this, qemuServiceConnection)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        startVncConnection()
    }

    override fun onPause() {
        pauseVncConnection()
        super.onPause()
    }

    private fun setupInputControls() {
        binding.bootOverlay.setOnClickListener {
            viewModel.sendAnyKeyToQemu()
        }

        binding.btnSendAnyKey.setOnClickListener {
            sendSpaceKey()
        }

        binding.btnSpaceKey.setOnClickListener {
            sendSpaceKey()
        }

        binding.btnShowKeyboard.setOnClickListener {
            QemuKeyboardInputHelper.showKeyboard(this, binding.qemuKeyboardInput)
        }

        binding.btnShowKeyboardOverlay.setOnClickListener {
            QemuKeyboardInputHelper.showKeyboard(this, binding.qemuKeyboardInput)
        }

        binding.btnCloseDisplay.setOnClickListener {
            QemuKeyboardInputHelper.hideKeyboard(this, binding.qemuKeyboardInput)
            finish()
        }

        binding.btnStopWindowsDisplay.setOnClickListener {
            QemuServiceController.stopLaunch(this)
            finish()
        }

        QemuKeyboardInputHelper.bindEditText(
            editText = binding.qemuKeyboardInput,
            scope = lifecycleScope,
            onMonitorError = { message ->
                binding.vncStatusText.text = getString(
                    com.w11mobile.R.string.windows_display_monitor_error,
                    message,
                )
            },
        )
    }

    private fun sendSpaceKey() {
        viewModel.sendAnyKeyToQemu()
    }

    private fun pauseVncConnection() {
        connectJob?.cancel()
        connectJob = null
        vncClient?.close()
        vncClient = null
    }

    override fun onDestroy() {
        pauseVncConnection()
        super.onDestroy()
    }

    private fun startVncConnection() {
        if (connectJob?.isActive == true) {
            return
        }
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
                if (!probe.rfbReady) {
                    if (attempt >= 3 && attempt % 5 == 0) {
                        val detail = probe.error?.message ?: "VNC сервер ще не готовий"
                        QemuRuntimeEvents.publishStatus(
                            "VNC $detail (спроба $attempt/$MAX_CONNECT_ATTEMPTS). " +
                                "UEFI-текст у логу — не екран Windows.",
                        )
                    }
                    delay(RETRY_DELAY_MS)
                    continue
                }

                vncClient?.close()
                val client = MinimalVncClient(
                    host = VncEndpoint.HOST,
                    port = VncEndpoint.TCP_PORT,
                )
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
                                                "VNC підключено ${bitmap.width}x${bitmap.height} — " +
                                                    "зображення Windows на сенсорному екрані",
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
                    if (viewModel.vncConnected.value != true && attempt >= 3 && attempt % 5 == 0) {
                        QemuRuntimeEvents.publishStatus(
                            "VNC RFB помилка на ${VncEndpoint.HOST}:${VncEndpoint.TCP_PORT} (спроба $attempt): " +
                                "${error.javaClass.simpleName}: ${error.message}",
                        )
                    } else if (viewModel.vncConnected.value == true && attempt % 10 == 0) {
                        QemuRuntimeEvents.publishStatus(
                            "VNC перепідключення (спроба $attempt) після розриву з'єднання…",
                        )
                    }
                    client.close()
                    if (vncClient === client) {
                        vncClient = null
                    }

                    val postConnectExit = QemuProcessSession.resolvedExitCodeOrNull()
                    if (postConnectExit != null) {
                        handleQemuProcessDeath(postConnectExit)
                        return@launch
                    }
                    delay(
                        if (viewModel.vncConnected.value == true) {
                            RECONNECT_DELAY_MS
                        } else {
                            RETRY_DELAY_MS
                        },
                    )
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

        private const val MAX_CONNECT_ATTEMPTS = 60
        private const val RETRY_DELAY_MS = 1_500L
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
