package com.example.candlealert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView(
    context: Context,
    private val accent: Int,
    private val primary: Int,
    private val muted: Int,
    private val darkTheme: Boolean,
    private val timeframeMinutesProvider: () -> Int
) : View(context) {
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG)
    private val number = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digital = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringTrack = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        tick.style = Paint.Style.STROKE
        tick.strokeCap = Paint.Cap.ROUND
        hand.strokeCap = Paint.Cap.ROUND
        center.style = Paint.Style.FILL
        number.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        digital.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        digital.textAlign = Paint.Align.CENTER
        ring.style = Paint.Style.STROKE
        ring.strokeCap = Paint.Cap.ROUND
        ringTrack.style = Paint.Style.STROKE
        ringTrack.strokeCap = Paint.Cap.ROUND
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The Home screen supplies a square canvas exactly equal to the phone width.
        // Use that full square so the outer clock edge aligns with both screen edges.
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = (size / 2f - 12f).coerceAtLeast(1f)

        face.style = Paint.Style.FILL
        face.color = if (darkTheme) 0xFF191D24.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, radius + 12f, face)
        face.style = Paint.Style.STROKE
        face.strokeWidth = 1.5f
        face.color = if (darkTheme) 0xFF3A414C.toInt() else 0xFFE1E6ED.toInt()
        canvas.drawCircle(cx, cy, radius + 12f, face)
        face.style = Paint.Style.FILL
        face.color = if (darkTheme) 0xFF20252D.toInt() else 0xFFF8FAFC.toInt()
        canvas.drawCircle(cx, cy, radius, face)

        val nowMs = System.currentTimeMillis()
        val tfSeconds = timeframeMinutesProvider().coerceAtLeast(1).toLong() * 60L
        val periodMs = tfSeconds * 1000L
        val elapsedMs = nowMs % periodMs
        val progress = elapsedMs.toFloat() / periodMs.toFloat()
        val ringRadius = radius + 7f

        ringTrack.color = if (darkTheme) 0xFF3A414C.toInt() else 0xFFDDE4EC.toInt()
        ringTrack.strokeWidth = 10f
        canvas.drawCircle(cx, cy, ringRadius, ringTrack)
        ring.color = accent
        ring.strokeWidth = 10f
        canvas.drawArc(RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius), -90f, progress * 360f, false, ring)

        for (i in 0 until 60) {
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val outer = radius - 8f
            val inner = if (i % 5 == 0) radius - 21f else radius - 15f
            tick.color = if (i % 5 == 0) primary else muted
            tick.alpha = if (i % 5 == 0) 190 else 75
            tick.strokeWidth = if (i % 5 == 0) 3f else 1.3f
            canvas.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner, cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, tick)
        }

        number.textAlign = Paint.Align.CENTER
        number.textSize = radius * 0.12f
        number.color = primary
        number.alpha = 225
        for (h in 1..12) {
            val angle = Math.toRadians(h * 30.0 - 90.0)
            val nr = radius - 37f
            canvas.drawText(h.toString(), cx + cos(angle).toFloat() * nr, cy + sin(angle).toFloat() * nr - (number.ascent() + number.descent()) / 2f, number)
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val millis = cal.get(Calendar.MILLISECOND)
        val secondFloat = second + millis / 1000f
        val minuteFloat = minute + secondFloat / 60f
        val hourFloat = (hour % 12) + minuteFloat / 60f

        drawHand(canvas, cx, cy, radius * 0.50f, hourFloat * 30f - 90f, 9f, primary)
        drawHand(canvas, cx, cy, radius * 0.70f, minuteFloat * 6f - 90f, 6f, primary)
        drawHand(canvas, cx, cy, radius * 0.82f, secondFloat * 6f - 90f, 2.5f, accent)

        center.color = primary
        canvas.drawCircle(cx, cy, 8f, center)
        center.color = accent
        canvas.drawCircle(cx, cy, 3.5f, center)

        digital.color = primary
        digital.textSize = radius * 0.145f
        val timeText = String.format(Locale.getDefault(), "%02d:%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), minute, second)
        canvas.drawText(timeText, cx, cy + radius * 0.30f, digital)
        postInvalidateDelayed(80)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, length: Float, degrees: Float, width: Float, color: Int) {
        hand.color = color
        hand.strokeWidth = width
        val radians = Math.toRadians(degrees.toDouble())
        canvas.drawLine(cx, cy, cx + cos(radians).toFloat() * length, cy + sin(radians).toFloat() * length, hand)
    }
}