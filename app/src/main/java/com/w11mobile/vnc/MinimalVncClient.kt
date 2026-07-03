package com.w11mobile.vnc

import android.graphics.Bitmap
import android.util.Log
import com.w11mobile.core.environment.QemuNativeLauncher
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESKeySpec

/**
 * Minimal RFB 3.8 client for localhost QEMU VNC (touch pointer + keys).
 */
class MinimalVncClient(
    private val host: String = QemuNativeLauncher.VNC_HOST,
    private val port: Int = QemuNativeLauncher.VNC_PORT,
    private val password: String = "",
) {
    interface FrameListener {
        fun onStatus(message: String)
        fun onFrame(bitmap: Bitmap)
        fun onDisconnected(error: String?)
    }

    @Volatile
    private var running = false

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private var frameWidth = 0
    private var frameHeight = 0
    private var bytesPerPixel = 4
    private var redShift = 16
    private var greenShift = 8
    private var blueShift = 0

    fun connect(listener: FrameListener) {
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                tcpNoDelay = true
                soTimeout = READ_TIMEOUT_MS
            }
        } catch (error: Exception) {
            VncConnectionDiagnostics.logSocketFailure("RFB handshake connect", host, port, error)
            throw error
        }
        input = DataInputStream(socket!!.getInputStream())
        output = DataOutputStream(socket!!.getOutputStream())

        performHandshake()
        readServerInit()
        setPreferredPixelFormat()
        setEncodings(intArrayOf(ENCODING_RAW))
        running = true

        if (frameWidth <= 0 || frameHeight <= 0) {
            frameWidth = DEFAULT_FRAME_WIDTH
            frameHeight = DEFAULT_FRAME_HEIGHT
            listener.onStatus(
                "VNC очікує ramfb ${frameWidth}x$frameHeight (QEMU ще не повідомив розмір)",
            )
        } else {
            listener.onStatus("VNC підключено ${frameWidth}x$frameHeight")
        }

        var bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        requestFullUpdate(incremental = false)

        while (running) {
            when (input!!.readUnsignedByte()) {
                SERVER_FRAMEBUFFER_UPDATE -> {
                    bitmap = handleFramebufferUpdate(bitmap, listener)
                }
                SERVER_SET_COLOR_MAP -> skipSetColorMapEntries()
                SERVER_BELL -> Unit
                SERVER_CUT_TEXT -> skipCutText()
                else -> throw IllegalStateException("Unsupported VNC server message")
            }
        }
    }

    fun sendPointer(x: Int, y: Int, pressed: Boolean) {
        val out = output ?: return
        synchronized(out) {
            out.writeByte(CLIENT_POINTER_EVENT)
            out.writeByte(if (pressed) 1 else 0)
            out.writeShort(x.coerceIn(0, frameWidth.coerceAtLeast(1) - 1))
            out.writeShort(y.coerceIn(0, frameHeight.coerceAtLeast(1) - 1))
            out.flush()
        }
    }

    fun sendKey(keySym: Int) {
        val out = output ?: return
        synchronized(out) {
            out.writeByte(CLIENT_KEY_EVENT)
            out.writeByte(1)
            out.writeShort(0)
            out.writeInt(keySym)
            out.writeByte(CLIENT_KEY_EVENT)
            out.writeByte(0)
            out.writeShort(0)
            out.writeInt(keySym)
            out.flush()
        }
    }

    fun sendAnyKey() {
        sendKey(KEYSYM_SPACE)
    }

    fun close() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        input = null
        output = null
    }

    private fun performHandshake() {
        val inStream = input ?: error("No input stream")
        val outStream = output ?: error("No output stream")

        val serverVersion = readLine(inStream)
        require(serverVersion.startsWith("RFB ")) { "Invalid VNC banner: $serverVersion" }
        outStream.writeBytes(CLIENT_VERSION)
        outStream.flush()

        val securityCount = inStream.readUnsignedByte()
        require(securityCount > 0) { "VNC server rejected connection" }
        val securityTypes = IntArray(securityCount) { inStream.readUnsignedByte() }
        val chosen = when {
            securityTypes.contains(SECURITY_NONE) -> SECURITY_NONE
            securityTypes.contains(SECURITY_VNC_AUTH) -> SECURITY_VNC_AUTH
            else -> error("Unsupported VNC security types: ${securityTypes.joinToString()}")
        }
        outStream.writeByte(chosen)
        outStream.flush()

        when (chosen) {
            SECURITY_NONE -> {
                val result = inStream.readInt()
                require(result == 0) { "VNC security handshake failed ($result)" }
            }

            SECURITY_VNC_AUTH -> {
                val challenge = ByteArray(16)
                inStream.readFully(challenge)
                outStream.write(encryptVncPassword(password, challenge))
                outStream.flush()
                val result = inStream.readInt()
                require(result == 0) { "VNC authentication failed ($result)" }
            }
        }

        outStream.writeByte(1) // shared desktop
        outStream.flush()
    }

    private fun readServerInit() {
        val inStream = input ?: error("No input stream")
        frameWidth = inStream.readUnsignedShort()
        frameHeight = inStream.readUnsignedShort()
        readPixelFormat(inStream)
        val nameLength = inStream.readInt()
        if (nameLength > 0) {
            inStream.readFully(ByteArray(nameLength))
        }
    }

    private fun readPixelFormat(inStream: DataInputStream) {
        bytesPerPixel = inStream.readUnsignedByte() / 8
        inStream.readUnsignedByte() // depth
        inStream.readUnsignedByte() // big endian
        inStream.readUnsignedByte() // true color
        inStream.readUnsignedShort() // red max
        inStream.readUnsignedShort() // green max
        inStream.readUnsignedShort() // blue max
        redShift = inStream.readUnsignedByte()
        greenShift = inStream.readUnsignedByte()
        blueShift = inStream.readUnsignedByte()
        inStream.readFully(ByteArray(3)) // padding
        if (bytesPerPixel <= 0) {
            bytesPerPixel = 4
        }
    }

    private fun setPreferredPixelFormat() {
        val out = output ?: return
        bytesPerPixel = 4
        redShift = 16
        greenShift = 8
        blueShift = 0
        out.writeByte(CLIENT_SET_PIXEL_FORMAT)
        out.writeByte(0)
        out.writeShort(0)
        out.writeByte(32) // bits per pixel
        out.writeByte(24) // depth
        out.writeByte(0) // little endian
        out.writeByte(1) // true color
        out.writeShort(255)
        out.writeShort(255)
        out.writeShort(255)
        out.writeByte(redShift)
        out.writeByte(greenShift)
        out.writeByte(blueShift)
        out.writeByte(0)
        out.writeByte(0)
        out.writeByte(0)
        out.flush()
    }

    private fun setEncodings(encodings: IntArray) {
        val out = output ?: return
        out.writeByte(CLIENT_SET_ENCODINGS)
        out.writeByte(0)
        out.writeShort(encodings.size)
        encodings.forEach { encoding ->
            out.writeInt(encoding)
        }
        out.flush()
    }

    private fun requestFullUpdate(incremental: Boolean) {
        val out = output ?: return
        out.writeByte(CLIENT_FRAMEBUFFER_UPDATE_REQUEST)
        out.writeByte(if (incremental) 1 else 0)
        out.writeShort(0)
        out.writeShort(0)
        out.writeShort(frameWidth)
        out.writeShort(frameHeight)
        out.flush()
    }

    private fun handleFramebufferUpdate(bitmap: Bitmap, listener: FrameListener): Bitmap {
        val inStream = input ?: return bitmap
        inStream.readUnsignedByte() // padding
        val rectangleCount = inStream.readUnsignedShort()
        var workingBitmap = bitmap
        repeat(rectangleCount) {
            val x = inStream.readUnsignedShort()
            val y = inStream.readUnsignedShort()
            val width = inStream.readUnsignedShort()
            val height = inStream.readUnsignedShort()
            val encoding = inStream.readInt()
            require(encoding == ENCODING_RAW) { "Unsupported VNC encoding: $encoding" }

            val requiredWidth = maxOf(workingBitmap.width, x + width)
            val requiredHeight = maxOf(workingBitmap.height, y + height)
            if (requiredWidth > workingBitmap.width || requiredHeight > workingBitmap.height) {
                frameWidth = requiredWidth
                frameHeight = requiredHeight
                workingBitmap = Bitmap.createBitmap(
                    requiredWidth,
                    requiredHeight,
                    Bitmap.Config.ARGB_8888,
                )
            }

            val rowBytes = width * bytesPerPixel
            val buffer = ByteArray(rowBytes * height)
            inStream.readFully(buffer)
            copyRawRect(workingBitmap, x, y, width, height, buffer)
        }
        listener.onFrame(workingBitmap)
        requestFullUpdate(incremental = true)
        return workingBitmap
    }

    private fun copyRawRect(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        buffer: ByteArray,
    ) {
        val pixels = IntArray(width)
        var offset = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                val pixelOffset = offset + col * bytesPerPixel
                val value = readPixel(buffer, pixelOffset)
                val red = (value shr redShift) and 0xFF
                val green = (value shr greenShift) and 0xFF
                val blue = (value shr blueShift) and 0xFF
                pixels[col] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
            bitmap.setPixels(pixels, 0, width, x, y + row, width, 1)
            offset += width * bytesPerPixel
        }
    }

    private fun readPixel(buffer: ByteArray, offset: Int): Int {
        return when (bytesPerPixel) {
            4 -> {
                (buffer[offset].toInt() and 0xFF) or
                    ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                    ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
                    ((buffer[offset + 3].toInt() and 0xFF) shl 24)
            }

            3 -> {
                (buffer[offset].toInt() and 0xFF) or
                    ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                    ((buffer[offset + 2].toInt() and 0xFF) shl 16)
            }

            else -> buffer[offset].toInt() and 0xFF
        }
    }

    private fun skipSetColorMapEntries() {
        val inStream = input ?: return
        inStream.readUnsignedByte() // padding
        inStream.readUnsignedShort() // first color
        val count = inStream.readUnsignedShort()
        inStream.readFully(ByteArray(count * 6))
    }

    private fun skipCutText() {
        val inStream = input ?: return
        inStream.readUnsignedByte()
        inStream.readUnsignedByte()
        inStream.readUnsignedByte()
        val length = inStream.readInt()
        if (length > 0) {
            inStream.readFully(ByteArray(length))
        }
    }

    private fun readLine(inStream: DataInputStream): String {
        val builder = StringBuilder()
        while (true) {
            val byte = inStream.read()
            if (byte == -1) {
                throw EOFException("VNC connection closed")
            }
            if (byte == '\n'.code) {
                break
            }
            builder.append(byte.toChar())
        }
        return builder.toString()
    }

    companion object {
        const val KEYSYM_SPACE = 0x0020

        private const val DEFAULT_FRAME_WIDTH = 1280
        private const val DEFAULT_FRAME_HEIGHT = 800
        private const val CLIENT_VERSION = "RFB 003.008\n"
        private const val SECURITY_NONE = 1
        private const val SECURITY_VNC_AUTH = 2
        private const val CLIENT_SET_PIXEL_FORMAT = 0
        private const val CLIENT_SET_ENCODINGS = 2
        private const val CLIENT_FRAMEBUFFER_UPDATE_REQUEST = 3
        private const val CLIENT_KEY_EVENT = 4
        private const val CLIENT_POINTER_EVENT = 5
        private const val SERVER_FRAMEBUFFER_UPDATE = 0
        private const val SERVER_SET_COLOR_MAP = 1
        private const val SERVER_BELL = 2
        private const val SERVER_CUT_TEXT = 3
        private const val ENCODING_RAW = 0
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 30_000

        private fun encryptVncPassword(password: String, challenge: ByteArray): ByteArray {
            val keyBytes = ByteArray(8)
            password.toByteArray(Charsets.ISO_8859_1).copyInto(
                destination = keyBytes,
                endIndex = minOf(8, password.length),
            )
            for (index in keyBytes.indices) {
                keyBytes[index] = reverseBits(keyBytes[index])
            }
            val keySpec = DESKeySpec(keyBytes)
            val key = SecretKeyFactory.getInstance("DES").generateSecret(keySpec)
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.doFinal(challenge)
        }

        private fun reverseBits(value: Byte): Byte {
            var input = value.toInt() and 0xFF
            var output = 0
            repeat(8) {
                output = (output shl 1) or (input and 1)
                input = input shr 1
            }
            return output.toByte()
        }
    }
}
