package com.w11mobile.vnc

import android.util.Log
import com.w11mobile.core.environment.QemuNativeLauncher
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

object VncConnectionDiagnostics {
    private const val TAG = "VncConnection"

    data class ProbeResult(
        val host: String,
        val port: Int,
        val open: Boolean,
        val error: Throwable? = null,
    )

    fun probePort(
        host: String = QemuNativeLauncher.VNC_HOST,
        port: Int = QemuNativeLauncher.VNC_PORT,
        timeoutMs: Int = 500,
    ): ProbeResult {
        require(host == QemuNativeLauncher.VNC_HOST) {
            "VNC must use explicit IPv4 loopback, not hostname aliases"
        }
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                ProbeResult(host = host, port = port, open = true)
            }
        } catch (error: Exception) {
            logSocketFailure("port probe", host, port, error)
            ProbeResult(host = host, port = port, open = false, error = error)
        }
    }

    fun logSocketFailure(stage: String, host: String, port: Int, error: Throwable) {
        val kind = classify(error)
        Log.e(
            TAG,
            "VNC $stage failed for $host:$port [$kind]: ${error.javaClass.simpleName}: ${error.message}",
            error,
        )
    }

    fun classify(error: Throwable): String = when (error) {
        is ConnectException -> "Connection Refused (port closed or not listening)"
        is SocketTimeoutException -> "Connection Timeout"
        is SocketException -> when {
            error.message?.contains("EPERM", ignoreCase = true) == true -> "Permission Denied"
            error.message?.contains("EACCES", ignoreCase = true) == true -> "Permission Denied"
            error.message?.contains("ECONNREFUSED", ignoreCase = true) == true ->
                "Connection Refused (port closed or not listening)"
            else -> "Socket Error"
        }
        else -> "Other"
    }
}
