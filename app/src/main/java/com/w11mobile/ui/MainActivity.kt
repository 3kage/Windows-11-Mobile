package com.w11mobile.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.w11mobile.R
import com.w11mobile.core.environment.ImageSource
import com.w11mobile.core.environment.SetupStep
import com.w11mobile.core.environment.WindowsImageArch
import com.w11mobile.databinding.ActivityMainBinding
import com.w11mobile.service.QemuServiceController
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val qemuServiceConnection = QemuServiceController.createConnection(
        onConnected = { service -> viewModel.onQemuServiceConnected(service) },
    )

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers do not support persistable permissions.
        }
        val displayName = queryDisplayName(uri)
        viewModel.onLocalImageSelected(uri, displayName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupClickListeners()
        setupQemuKeyboardInput()
    }

    override fun onStart() {
        super.onStart()
        QemuServiceController.bind(this, qemuServiceConnection)
    }

    override fun onStop() {
        QemuServiceController.unbind(this, qemuServiceConnection)
        super.onStop()
    }

    private fun setupQemuKeyboardInput() {
        QemuKeyboardInputHelper.bindEditText(
            editText = binding.qemuKeyboardInputMain,
            scope = lifecycleScope,
            onMonitorError = { message ->
                appendMonitorError(message)
            },
        )
    }

    private fun appendMonitorError(message: String) {
        viewModel.appendMonitorError(message)
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            binding.statusText.text = state.stepLabel
            binding.progressBar.isIndeterminate = state.isIndeterminate
            if (!state.isIndeterminate) {
                binding.progressBar.progress = state.progress
            }
            binding.progressPercentText.text = getString(R.string.progress_percent, state.progress)
            binding.progressPercentText.isVisible = state.isRunning && !state.isIndeterminate

            binding.terminalLogText.text = state.terminalLog
            binding.terminalLogScroll.post {
                binding.terminalLogScroll.fullScroll(android.view.View.FOCUS_DOWN)
            }

            binding.btnInitializeWindows.isEnabled = !state.isRunning
            binding.btnInitializeWindows.text = if (state.isRunning) {
                getString(R.string.initializing_windows)
            } else {
                getString(R.string.initialize_windows)
            }

            binding.btnLaunchWindows.isEnabled = state.canLaunchWindows && !state.isRunning
            binding.btnLaunchWindows.isVisible = state.environmentReady || state.step == SetupStep.COMPLETE

            binding.btnOpenTouchDisplay.isVisible = state.windowsSessionActive
            binding.btnStopWindows.isVisible = state.windowsSessionActive
            binding.btnSendAnyKey.isVisible = state.windowsSessionActive
            binding.btnShowKeyboardMain.isVisible = state.windowsSessionActive

            binding.btnImportLocalImage.isEnabled = !state.isRunning && !state.localImageUri.isNullOrBlank()
            binding.btnImportLocalImage.isVisible = state.imageSource == ImageSource.LOCAL

            binding.errorText.isVisible = !state.errorMessage.isNullOrBlank()
            binding.errorText.text = state.errorMessage

            val isUrlSource = state.imageSource == ImageSource.URL
            binding.windowsImageUrlLayout.isVisible = isUrlSource
            binding.localImageContainer.isVisible = !isUrlSource

            if (binding.imageSourceToggle.checkedButtonId == android.view.View.NO_ID ||
                binding.imageSourceToggle.checkedButtonId != sourceToToggleId(state.imageSource)
            ) {
                binding.imageSourceToggle.check(sourceToToggleId(state.imageSource))
            }

            if (binding.imageArchToggle.checkedButtonId == android.view.View.NO_ID ||
                binding.imageArchToggle.checkedButtonId != archToToggleId(state.windowsImageArch)
            ) {
                binding.imageArchToggle.check(archToToggleId(state.windowsImageArch))
            }

            if (binding.windowsImageUrlInput.text?.toString() != state.windowsImageUrl) {
                binding.windowsImageUrlInput.setText(state.windowsImageUrl)
            }

            binding.selectedLocalFileText.text = state.localImageName ?: state.localImageUri
                ?: getString(R.string.local_image_not_selected)
        }
    }

    private fun setupClickListeners() {
        binding.imageSourceToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setImageSource(
                if (checkedId == R.id.btnSourceLocal) ImageSource.LOCAL else ImageSource.URL,
            )
        }

        binding.imageArchToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setWindowsImageArch(
                when (checkedId) {
                    R.id.btnArchArm64 -> WindowsImageArch.ARM64
                    R.id.btnArchX86 -> WindowsImageArch.X86_64
                    else -> WindowsImageArch.AUTO
                },
            )
        }

        binding.windowsImageUrlInput.doAfterTextChanged { editable ->
            viewModel.setWindowsImageUrl(editable?.toString().orEmpty())
        }

        binding.btnPickLocalImage.setOnClickListener {
            pickImageLauncher.launch(SUPPORTED_IMAGE_TYPES)
        }

        binding.btnImportLocalImage.setOnClickListener {
            viewModel.importLocalImageOnly()
        }

        binding.btnInitializeWindows.setOnClickListener {
            viewModel.initializeWindows11()
        }

        binding.btnLaunchWindows.setOnClickListener {
            if (viewModel.uiState.value?.windowsSessionActive == true) return@setOnClickListener
            lifecycleScope.launch {
                val isoBootMode = viewModel.prepareWindowsLaunch() ?: return@launch
                viewModel.runWindowsLaunch(isoBootMode)
                openWindowsDisplay(isoBootMode)
            }
        }

        binding.btnOpenTouchDisplay.setOnClickListener {
            openWindowsDisplay(viewModel.isIsoBootModeCached())
        }

        binding.btnStopWindows.setOnClickListener {
            viewModel.stopWindowsSession()
        }

        binding.btnSendAnyKey.setOnClickListener {
            viewModel.sendAnyKeyToQemu()
        }

        binding.btnShowKeyboardMain.setOnClickListener {
            QemuKeyboardInputHelper.showKeyboard(this, binding.qemuKeyboardInputMain)
        }

        binding.btnClearLog.setOnClickListener {
            viewModel.clearLog()
        }
    }

    private fun openWindowsDisplay(showBootOverlay: Boolean) {
        startActivity(
            Intent(this, WindowsDisplayActivity::class.java).apply {
                putExtra(WindowsDisplayActivity.EXTRA_SHOW_BOOT_OVERLAY, showBootOverlay)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private fun sourceToToggleId(source: ImageSource): Int =
        if (source == ImageSource.LOCAL) R.id.btnSourceLocal else R.id.btnSourceUrl

    private fun archToToggleId(arch: WindowsImageArch): Int = when (arch) {
        WindowsImageArch.ARM64 -> R.id.btnArchArm64
        WindowsImageArch.X86_64 -> R.id.btnArchX86
        WindowsImageArch.AUTO -> R.id.btnArchAuto
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    companion object {
        private val SUPPORTED_IMAGE_TYPES = arrayOf(
            "application/octet-stream",
            "application/x-iso9660-image",
            "application/x-qemu-disk",
            "*/*",
        )
    }
}
