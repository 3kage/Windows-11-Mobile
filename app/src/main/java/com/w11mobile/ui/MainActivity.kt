package com.w11mobile.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.w11mobile.R
import com.w11mobile.core.environment.SetupStep
import com.w11mobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

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

            binding.errorText.isVisible = !state.errorMessage.isNullOrBlank()
            binding.errorText.text = state.errorMessage

            if (binding.windowsImageUrlInput.text?.toString() != state.windowsImageUrl) {
                binding.windowsImageUrlInput.setText(state.windowsImageUrl)
            }
        }
    }

    private fun setupClickListeners() {
        binding.windowsImageUrlInput.doAfterTextChanged { editable ->
            viewModel.setWindowsImageUrl(editable?.toString().orEmpty())
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
}
