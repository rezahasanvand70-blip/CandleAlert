package com.example.candlealert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView(
    context: Context,
    private val accent: Int,
    private val primary: Int,
    private val muted: Int
) : View(context) {

    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG)
    private val number = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastSecond = -1

    init {
        face.style = Paint.Style.FILL
        tick.style = Paint.Style.STROKE
        tick.strokeCap = Paint.Cap.ROUND
        hand.strokeCap = Paint.Cap.ROUND
        center.style = Paint.Style.FILL
        number.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * 0.43f

        // Subtle outer glow/ring.
        face.color = 0xFF0D2643.toInt()
        canvas.drawCircle(cx, cy, radius + 10f, face)
        face.color = 0xFF071A30.toInt()
        canvas.drawCircle(cx, cy, radius, face)

        // Minute/hour markers.
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

        // Hour numbers.
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

        val now = Calendar.getInstance()
        val ms = now.get(Calendar.MILLISECOND)
        val second = now.get(Calendar.SECOND) + ms / 1000f
        val minute = now.get(Calendar.MINUTE) + second / 60f
        val hour = (now.get(Calendar.HOUR) % 12) + minute / 60f

        // Hands with a restrained shadow for depth.
        drawHand(canvas, cx, cy, radius * 0.52f, hour * 30f - 90f, 8f, 0x66000000, 5f)
        drawHand(canvas, cx, cy, radius * 0.72f, minute * 6f - 90f, 5f, 0x66000000, 3f)
        drawHand(canvas, cx, cy, radius * 0.80f, second * 6f - 90f, 2.4f, 0x66000000, 1.5f)

        drawHand(canvas, cx, cy, radius * 0.52f, hour * 30f - 90f, 8f, primary, 0f)
        drawHand(canvas, cx, cy, radius * 0.72f, minute * 6f - 90f, 5f, primary, 0f)
        drawHand(canvas, cx, cy, radius * 0.80f, second * 6f - 90f, 2.4f, accent, 0f)

        center.color = primary
        canvas.drawCircle(cx, cy, 7f, center)
        center.color = accent
        canvas.drawCircle(cx, cy, 3f, center)

        if (now.get(Calendar.SECOND) != lastSecond) {
            lastSecond = now.get(Calendar.SECOND)
            postInvalidateDelayed(50)
        } else {
            postInvalidateDelayed(100)
        }
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
