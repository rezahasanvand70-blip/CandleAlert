package com.example.candlealert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView(
    context: Context,
    private val accent: Int,
    private val primary: Int,
    private val muted: Int,
    private val remainingSecondsProvider: () -> Long?
) : View(context) {

    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG)
    private val number = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerText = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        face.style = Paint.Style.FILL
        tick.style = Paint.Style.STROKE
        tick.strokeCap = Paint.Cap.ROUND
        hand.strokeCap = Paint.Cap.ROUND
        center.style = Paint.Style.FILL
        number.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        centerText.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        centerText.textAlign = Paint.Align.CENTER
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * 0.43f

        face.color = 0xFF0D2643.toInt()
        canvas.drawCircle(cx, cy, radius + 10f, face)
        face.color = 0xFF071A30.toInt()
        canvas.drawCircle(cx, cy, radius, face)

        for (i in 0 until 60) {
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val outer = radius - 8f
            val inner = if (i % 5 == 0) radius - 20f else radius - 14f
            tick.color = if (i % 5 == 0) primary else muted
            tick.alpha = if (i % 5 == 0) 220 else 105
            tick.strokeWidth = if (i % 5 == 0) 3.2f else 1.4f
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                tick
            )
        }

        number.textAlign = Paint.Align.CENTER
        number.textSize = radius * 0.12f
        number.color = primary
        number.alpha = 225
        for (h in 1..12) {
            val angle = Math.toRadians(h * 30.0 - 90.0)
            val nr = radius - 34f
            canvas.drawText(
                h.toString(),
                cx + cos(angle).toFloat() * nr,
                cy + sin(angle).toFloat() * nr - (number.ascent() + number.descent()) / 2f,
                number
            )
        }

        val remaining = (remainingSecondsProvider() ?: 0L).coerceAtLeast(0L)
        val minute = (remaining % 3600L) / 60f
        val second = (remaining % 60L).toFloat()

        // Countdown timer: minute hand = remaining minutes in the current hour,
        // second hand = remaining seconds. The hands move continuously toward zero.
        drawHand(canvas, cx, cy, radius * 0.52f, minute * 6f - 90f, 8f, 0x66000000, 5f)
        drawHand(canvas, cx, cy, radius * 0.72f, minute * 6f - 90f, 5f, 0x66000000, 3f)
        drawHand(canvas, cx, cy, radius * 0.80f, second * 6f - 90f, 2.4f, 0x66000000, 1.5f)

        drawHand(canvas, cx, cy, radius * 0.72f, minute * 6f - 90f, 5f, primary, 0f)
        drawHand(canvas, cx, cy, radius * 0.80f, second * 6f - 90f, 2.4f, accent, 0f)

        center.color = primary
        canvas.drawCircle(cx, cy, 7f, center)
        center.color = accent
        canvas.drawCircle(cx, cy, 3f, center)

        centerText.color = primary
        centerText.textSize = radius * 0.16f
        val h = remaining / 3600L
        val m = (remaining % 3600L) / 60L
        val s = remaining % 60L
        val text = if (h > 0) {
            String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
        }
        canvas.drawText(text, cx, cy + radius * 0.28f, centerText)

        postInvalidateDelayed(80)
    }

    private fun drawHand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        length: Float,
        degrees: Float,
        width: Float,
        color: Int,
        shadow: Float
    ) {
        hand.color = color
        hand.strokeWidth = width
        if (shadow > 0f) {
            canvas.drawLine(
                cx + shadow,
                cy + shadow,
                cx + cos(Math.toRadians(degrees.toDouble())).toFloat() * length + shadow,
                cy + sin(Math.toRadians(degrees.toDouble())).toFloat() * length + shadow,
                hand
            )
        } else {
            canvas.drawLine(
                cx,
                cy,
                cx + cos(Math.toRadians(degrees.toDouble())).toFloat() * length,
                cy + sin(Math.toRadians(degrees.toDouble())).toFloat() * length,
                hand
            )
        }
    }
}
