package com.w11mobile.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
        viewModel.terminalLog.observe(this) { log ->
            binding.terminalLogText.text = log
            binding.terminalLogScroll.post {
                binding.terminalLogScroll.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }

        viewModel.isInitializing.observe(this) { isRunning ->
            binding.btnInitializeWindows.isEnabled = !isRunning
            binding.btnInitializeWindows.text = if (isRunning) {
                getString(com.w11mobile.R.string.initializing_windows)
            } else {
                getString(com.w11mobile.R.string.initialize_windows)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnInitializeWindows.setOnClickListener {
            viewModel.initializeWindows11()
        }

        binding.btnClearLog.setOnClickListener {
            viewModel.clearLog()
        }
    }
}
