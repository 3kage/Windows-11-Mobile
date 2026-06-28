package com.w11mobile.core.environment

import android.content.Context
import com.w11mobile.BuildConfig

class SetupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var windowsImageUrl: String
        get() = prefs.getString(KEY_WINDOWS_IMAGE_URL, BuildConfig.DEFAULT_WINDOWS_IMAGE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_WINDOWS_IMAGE_URL, value.trim()).apply()

    var setupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    companion object {
        private const val PREFS_NAME = "environment_setup"
        private const val KEY_WINDOWS_IMAGE_URL = "windows_image_url"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }
}
