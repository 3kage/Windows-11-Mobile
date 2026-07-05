package com.w11mobile.core.environment

/**
 * Lightweight bridge from background QEMU/VNC layers back to [com.w11mobile.ui.MainViewModel].
 */
object QemuRuntimeEvents {
    @Volatile
    var onFatalError: ((String) -> Unit)? = null

    @Volatile
    var onStatus: ((String) -> Unit)? = null

    @Volatile
    var onTerminalLine: ((String) -> Unit)? = null

    @Volatile
    var onSessionEnded: ((Int) -> Unit)? = null

    fun publishFatal(message: String) {
        onFatalError?.invoke(message)
    }

    fun publishStatus(message: String) {
        onStatus?.invoke(message)
    }

    fun publishTerminalLine(line: String) {
        onTerminalLine?.invoke(line)
    }

    fun publishSessionEnded(exitCode: Int) {
        onSessionEnded?.invoke(exitCode)
    }
}
