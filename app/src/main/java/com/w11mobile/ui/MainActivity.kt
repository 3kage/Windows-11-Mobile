package com.w11mobile.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.w11mobile.R
import com.w11mobile.core.environment.ImageSource
import com.w11mobile.core.environment.SetupStep
import com.w11mobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

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
            viewModel.launchWindows11()
        }

        binding.btnClearLog.setOnClickListener {
            viewModel.clearLog()
        }
    }

    private fun sourceToToggleId(source: ImageSource): Int =
        if (source == ImageSource.LOCAL) R.id.btnSourceLocal else R.id.btnSourceUrl

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
