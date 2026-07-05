package com.w11mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class VncFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onPointerEvent: ((frameX: Int, frameY: Int, pressed: Boolean) -> Unit)? = null

    private var frameBitmap: Bitmap? = null
    private val drawMatrix = Matrix()
    private val drawRect = RectF()

    fun updateFrame(bitmap: Bitmap) {
        frameBitmap = bitmap
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = frameBitmap ?: return
        drawRect.set(0f, 0f, width.toFloat(), height.toFloat())
        drawMatrix.setRectToRect(
            RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
            drawRect,
            Matrix.ScaleToFit.CENTER,
        )
        canvas.drawBitmap(bitmap, drawMatrix, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bitmap = frameBitmap ?: return true
        val mapped = mapTouchToFrame(event.x, event.y, bitmap.width, bitmap.height) ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onPointerEvent?.invoke(mapped.first, mapped.second, true)
            MotionEvent.ACTION_UP -> onPointerEvent?.invoke(mapped.first, mapped.second, false)
        }
        return true
    }

    private fun mapTouchToFrame(
        touchX: Float,
        touchY: Float,
        frameWidth: Int,
        frameHeight: Int,
    ): Pair<Int, Int>? {
        if (width == 0 || height == 0) {
            return null
        }

        val scale = minOf(
            width.toFloat() / frameWidth.toFloat(),
            height.toFloat() / frameHeight.toFloat(),
        )
        val drawnWidth = frameWidth * scale
        val drawnHeight = frameHeight * scale
        val offsetX = (width - drawnWidth) / 2f
        val offsetY = (height - drawnHeight) / 2f

        if (touchX < offsetX || touchY < offsetY ||
            touchX > offsetX + drawnWidth || touchY > offsetY + drawnHeight
        ) {
            return null
        }

        val frameX = ((touchX - offsetX) / scale).toInt().coerceIn(0, frameWidth - 1)
        val frameY = ((touchY - offsetY) / scale).toInt().coerceIn(0, frameHeight - 1)
        return frameX to frameY
    }
}
