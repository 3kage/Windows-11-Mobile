package com.w11mobile.core.environment

import android.content.Context
import com.w11mobile.BuildConfig

class SetupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var windowsImageUrl: String
        get() = prefs.getString(KEY_WINDOWS_IMAGE_URL, BuildConfig.DEFAULT_WINDOWS_IMAGE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_WINDOWS_IMAGE_URL, value.trim()).apply()

    var imageSource: ImageSource
        get() = ImageSource.valueOf(
            prefs.getString(KEY_IMAGE_SOURCE, ImageSource.URL.name) ?: ImageSource.URL.name,
        )
        set(value) = prefs.edit().putString(KEY_IMAGE_SOURCE, value.name).apply()

    var localImageUri: String?
        get() = prefs.getString(KEY_LOCAL_IMAGE_URI, null)
        set(value) = prefs.edit().putString(KEY_LOCAL_IMAGE_URI, value).apply()

    var localImageName: String?
        get() = prefs.getString(KEY_LOCAL_IMAGE_NAME, null)
        set(value) = prefs.edit().putString(KEY_LOCAL_IMAGE_NAME, value).apply()

    var windowsImageArch: WindowsImageArch
        get() = WindowsImageArch.valueOf(
            prefs.getString(KEY_WINDOWS_IMAGE_ARCH, WindowsImageArch.AUTO.name)
                ?: WindowsImageArch.AUTO.name,
        )
        set(value) = prefs.edit().putString(KEY_WINDOWS_IMAGE_ARCH, value.name).apply()

    var setupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    companion object {
        private const val PREFS_NAME = "environment_setup"
        private const val KEY_WINDOWS_IMAGE_URL = "windows_image_url"
        private const val KEY_IMAGE_SOURCE = "image_source"
        private const val KEY_LOCAL_IMAGE_URI = "local_image_uri"
        private const val KEY_LOCAL_IMAGE_NAME = "local_image_name"
        private const val KEY_WINDOWS_IMAGE_ARCH = "windows_image_arch"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }
}
