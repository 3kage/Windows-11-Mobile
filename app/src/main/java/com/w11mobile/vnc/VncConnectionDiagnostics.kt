package com.w11mobile.vnc

import android.util.Log
import com.w11mobile.vnc.VncEndpoint
import java.io.BufferedInputStream
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
        val rfbReady: Boolean = false,
        val error: Throwable? = null,
    )

    fun probePort(
        host: String = VncEndpoint.HOST,
        port: Int = VncEndpoint.TCP_PORT,
        timeoutMs: Int = 500,
    ): ProbeResult = probeRfb(host, port, timeoutMs)

    /**
     * Verifies TCP + RFB banner without leaving a half-open client that races the real session.
     */
    fun probeRfb(
        host: String = VncEndpoint.HOST,
        port: Int = VncEndpoint.TCP_PORT,
        timeoutMs: Int = 1500,
    ): ProbeResult {
        require(host == VncEndpoint.HOST) {
            "VNC must use explicit IPv4 loopback, not hostname aliases"
        }
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.tcpNoDelay = true
                socket.soTimeout = timeoutMs
                val input = BufferedInputStream(socket.getInputStream())
                val bannerBytes = ByteArray(4)
                val read = input.read(bannerBytes)
                val banner = if (read > 0) String(bannerBytes, 0, read) else ""
                val rfbReady = banner.startsWith("RFB ")
                if (!rfbReady) {
                    ProbeResult(
                        host = host,
                        port = port,
                        open = true,
                        rfbReady = false,
                        error = IllegalStateException("Unexpected VNC banner: ${banner.ifEmpty { "(empty)" }}"),
                    )
                } else {
                    ProbeResult(host = host, port = port, open = true, rfbReady = true)
                }
            }
        } catch (error: Exception) {
            logSocketFailure("RFB probe", host, port, error)
            ProbeResult(host = host, port = port, open = false, rfbReady = false, error = error)
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
