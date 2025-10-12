package com.example.b1void.camera.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.withSave
import kotlin.math.min

class FocusOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { Idle, Focusing, Success, Fail, Locked }

    private val dp = resources.displayMetrics.density

    private val ringPaintOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * dp
        color = Color.WHITE
    }
    private val ringPaintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.WHITE
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(6f * dp, 0f, 0f, 0x55000000.toInt())
        color = Color.TRANSPARENT
    }

    private val evPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 180
    }

    var mode: Mode = Mode.Idle
        set(value) { field = value; invalidate() }

    var showEv: Boolean = false
        set(value) { field = value; invalidate() }

    var evValue: Float = 0f
        set(value) { field = value; invalidate() }

    /** Show focus ring at specified coordinates and state. */
    fun showFocusAt(x: Float, y: Float, state: Mode) {
        translationX = x - width / 2f
        translationY = y - height / 2f
        mode = state
        animateAlpha()
    }

    /** Show EV slider near focus ring at (x,y) with current value in EV. */
    fun showEvSlider(atX: Float, atY: Float, value: Float) {
        translationX = atX - width / 2f
        translationY = atY - height / 2f
        evValue = value
        showEv = true
        invalidate()
    }

    /** Hide EV slider overlay. */
    fun hideEvSlider() { showEv = false; invalidate() }

    private fun animateAlpha() {
        alpha = 0.85f
        animate().alpha(1f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        scaleX = 1.2f; scaleY = 1.2f
        animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 8f * dp
        val color = when (mode) {
            Mode.Focusing -> Color.WHITE
            Mode.Success -> 0xFF00E676.toInt() // Samsung-like green
            Mode.Fail -> 0xFFFF5252.toInt()   // Samsung-like red
            Mode.Locked -> 0xFF00B8D4.toInt() // Cyan-ish
            Mode.Idle -> Color.TRANSPARENT
        }

        if (color != Color.TRANSPARENT) {
            ringPaintOuter.color = color
            ringPaintInner.color = color
            centerDotPaint.color = color
            canvas.withSave {
                // subtle shadow
                setLayerType(LAYER_TYPE_SOFTWARE, shadowPaint)
                canvas.drawCircle(cx, cy, r + 1.5f * dp, shadowPaint)
            }
            // double ring
            canvas.drawCircle(cx, cy, r, ringPaintOuter)
            canvas.drawCircle(cx, cy, r - 4f * dp, ringPaintInner)
            // center dot
            canvas.drawCircle(cx, cy, 2.5f * dp, centerDotPaint)
        }
        if (showEv) {
            canvas.withSave {
                val w = 6f * resources.displayMetrics.density
                val h = height * 0.7f
                val left = 0f
                val top = cy - h / 2
                drawRect(left, top, left + w, top + h, evPaint)
                // EV bubble could be drawn here
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Draw overlays on separate hardware layer to minimize overdraw/invalidate
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
}
