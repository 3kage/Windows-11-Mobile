package com.w11mobile.core.environment

/**
 * Lightweight bridge from background QEMU/VNC layers back to [com.w11mobile.ui.MainViewModel].
 */
object QemuRuntimeEvents {
    @Volatile
    var onFatalError: ((String) -> Unit)? = null

    fun publishFatal(message: String) {
        onFatalError?.invoke(message)
    }
}
