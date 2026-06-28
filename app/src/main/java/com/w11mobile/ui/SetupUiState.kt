package com.w11mobile.ui

import com.w11mobile.core.environment.ImageSource
import com.w11mobile.core.environment.SetupStep

data class SetupUiState(
    val step: SetupStep = SetupStep.IDLE,
    val stepLabel: String = SetupStep.IDLE.labelUk,
    val progress: Int = 0,
    val isIndeterminate: Boolean = false,
    val isRunning: Boolean = false,
    val environmentReady: Boolean = false,
    val canLaunchWindows: Boolean = false,
    val terminalLog: String = "",
    val imageSource: ImageSource = ImageSource.URL,
    val windowsImageUrl: String = "",
    val localImageUri: String? = null,
    val localImageName: String? = null,
    val errorMessage: String? = null,
)
