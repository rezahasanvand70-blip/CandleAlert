package com.example.candlealert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.time.*
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class AnalogClockView(
    context: Context,
    private val accent: Int,
    private val primary: Int,
    private val muted: Int,
    private val darkTheme: Boolean,
    private val timeframeMinutesProvider: () -> Int,
    private val onSessionToggle: (String) -> Unit
) : View(context) {

    private data class Session(
        val name: String,
        val zone: String,
        val startHour: Int,
        val endHour: Int,
        val color: Int
    )

    private val sessions = listOf(
        Session("Sydney", "Australia/Sydney", 22, 7, 0xFF1677FF.toInt()),
        Session("Tokyo", "Asia/Tokyo", 0, 9, 0xFF1677FF.toInt()),
        Session("Frankfurt", "Europe/Berlin", 7, 16, 0xFF1677FF.toInt()),
        Session("London", "Europe/London", 8, 17, 0xFF1677FF.toInt()),
        Session("New York", "America/New_York", 13, 22, 0xFF1677FF.toInt())
    )

    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG)
    private val number = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digital = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sessionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        tick.style = Paint.Style.STROKE
        tick.strokeCap = Paint.Cap.ROUND
        hand.strokeCap = Paint.Cap.ROUND
        center.style = Paint.Style.FILL
        number.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        digital.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        digital.textAlign = Paint.Align.CENTER
        ringTrack.style = Paint.Style.STROKE
        ringTrack.strokeCap = Paint.Cap.ROUND
        sessionPaint.style = Paint.Style.STROKE
        sessionPaint.strokeCap = Paint.Cap.BUTT
        labelPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        labelPaint.textAlign = Paint.Align.CENTER
        candlePaint.style = Paint.Style.STROKE
        candlePaint.strokeCap = Paint.Cap.ROUND
        isFocusable = false
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = (size / 2f - 22f).coerceAtLeast(1f)
        val prefs = context.getSharedPreferences("prefs", 0)
        val selected = prefs.getStringSet(
            "sessions",
            sessions.map { it.name }.toSet()
        ) ?: emptySet()
        val now = ZonedDateTime.now()

        face.style = Paint.Style.FILL
        face.color = if (darkTheme) 0xFF15191F.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, radius + 13f, face)
        face.style = Paint.Style.STROKE
        face.strokeWidth = 1.5f
        face.color = if (darkTheme) 0xFF3B434E.toInt() else 0xFFD8DEE6.toInt()
        canvas.drawCircle(cx, cy, radius + 13f, face)
        face.style = Paint.Style.FILL
        face.color = if (darkTheme) 0xFF1B2027.toInt() else 0xFFF7F9FB.toInt()
        canvas.drawCircle(cx, cy, radius, face)

        // Candle countdown: a dedicated circular progress ring around the whole clock.
        val candle = candleState()
        candlePaint.strokeWidth = radius * 0.035f
        candlePaint.color = if (darkTheme) 0xFF536171.toInt() else 0xFFDCE3EB.toInt()
        candlePaint.alpha = 220
        canvas.drawCircle(cx, cy, radius + 7f, candlePaint)

        candlePaint.color = accent
        candlePaint.alpha = 235
        val progressSweep = (candle.progress * 360f).coerceIn(0f, 359.9f)
        canvas.drawArc(
            RectF(cx - radius - 7f, cy - radius - 7f, cx + radius + 7f, cy + radius + 7f),
            -90f,
            progressSweep,
            false,
            candlePaint
        )

        // Broad session sectors inside the dial, following the visual language of a trader 24h clock.
        val bandBase = radius * 0.76f
        val bandGap = radius * 0.115f
        val bandWidth = radius * 0.090f

        sessions.forEachIndexed { index, session ->
            val rr = bandBase - index * bandGap
            drawSessionBand(canvas, cx, cy, rr, bandWidth, session, selected.contains(session.name), now)
        }

        // Outer 24-hour scale.
        for (h in 0 until 24) {
            val angle = Math.toRadians(h * 15.0 - 90.0)
            val outer = radius * 0.965f
            val inner = if (h % 3 == 0) radius * 0.885f else radius * 0.915f
            tick.color = primary
            tick.alpha = if (h % 3 == 0) 190 else 75
            tick.strokeWidth = if (h % 3 == 0) 2.4f else 1.0f
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                tick
            )
        }

        number.textAlign = Paint.Align.CENTER
        number.textSize = radius * 0.072f
        number.color = primary
        number.alpha = 225
        for (h in 0 until 24) {
            val angle = Math.toRadians(h * 15.0 - 90.0)
            val nr = radius * 0.835f
            canvas.drawText(
                h.toString().padStart(2, '0'),
                cx + cos(angle).toFloat() * nr,
                cy + sin(angle).toFloat() * nr - (number.ascent() + number.descent()) / 2f,
                number
            )
        }

        // Current time position.
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val millis = cal.get(Calendar.MILLISECOND)
        val currentHour = hour + minute / 60f + second / 3600f + millis / 3600000f
        val currentAngle = Math.toRadians(currentHour * 15.0 - 90.0)

        hand.color = accent
        hand.alpha = 220
        hand.strokeWidth = 2.5f
        canvas.drawLine(
            cx + cos(currentAngle).toFloat() * radius * 0.20f,
            cy + sin(currentAngle).toFloat() * radius * 0.20f,
            cx + cos(currentAngle).toFloat() * radius * 0.92f,
            cy + sin(currentAngle).toFloat() * radius * 0.92f,
            hand
        )

        // Mechanical analog hands.
        val secondFloat = second + millis / 1000f
        val minuteFloat = minute + secondFloat / 60f
        val hourFloat = hour + minuteFloat / 60f
        drawHand(canvas, cx, cy, radius * 0.24f, hourFloat * 30f - 90f, 7f, primary)
        drawHand(canvas, cx, cy, radius * 0.39f, minuteFloat * 6f - 90f, 4.5f, primary)
        drawHand(canvas, cx, cy, radius * 0.47f, secondFloat * 6f - 90f, 2f, accent)

        // Computer/digital clock is deliberately centered inside the analog clock.
        face.style = Paint.Style.FILL
        face.color = if (darkTheme) 0xFF1B2027.toInt() else 0xFFF7F9FB.toInt()
        canvas.drawRoundRect(
            RectF(cx - radius * 0.19f, cy - radius * 0.075f, cx + radius * 0.19f, cy + radius * 0.075f),
            radius * 0.035f, radius * 0.035f, face
        )
        digital.color = primary
        digital.textSize = radius * 0.082f
        val timeText = String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second)
        canvas.drawText(timeText, cx, cy - (digital.ascent() + digital.descent()) / 2f, digital)

        center.color = primary
        canvas.drawCircle(cx, cy, 6f, center)
        center.color = accent
        canvas.drawCircle(cx, cy, 2.5f, center)

        // Candle countdown text near the bottom of the dial, while the progress ring remains around it.
        digital.color = accent
        digital.textSize = radius * 0.052f
        val candleText = formatCandle(candle.remainingSeconds)
        canvas.drawText("CANDLE  $candleText", cx, cy + radius * 0.23f, digital)

        // Session names live directly inside their own broad sectors.
        labelPaint.textSize = radius * 0.050f
        sessions.forEachIndexed { index, session ->
            val rr = bandBase - index * bandGap
            val local = sessionLocalInterval(session, now)
            val mid = midpointAngle(local.first, local.second)
            val angle = Math.toRadians(mid * 15.0 - 90.0)
            labelPaint.color = if (darkTheme) ColorForLabel(session.color, true) else session.color
            labelPaint.alpha = if (selected.contains(session.name)) 245 else 55
            canvas.drawText(
                session.name,
                cx + cos(angle).toFloat() * rr,
                cy + sin(angle).toFloat() * rr - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint
            )
        }

        postInvalidateDelayed(120)
    }

    private data class CandleState(val progress: Float, val remainingSeconds: Long)

    private fun candleState(): CandleState {
        val tf = timeframeMinutesProvider().coerceAtLeast(1)
        val now = ZonedDateTime.now()
        val parts = (context.getSharedPreferences("prefs", 0).getString("open_market", "00:00") ?: "00:00").split(":")
        val openH = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val openM = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        var anchor = now.toLocalDate().atTime(openH, openM).atZone(now.zone)
        if (now.isBefore(anchor)) anchor = anchor.minusDays(1)
        val elapsed = Duration.between(anchor, now).toMillis()
        val period = tf * 60_000L
        val into = ((elapsed % period) + period) % period
        val remaining = period - into
        val progress = (into.toDouble() / period.toDouble()).toFloat()
        return CandleState(progress, (remaining / 1000L).coerceAtLeast(0L))
    }

    private fun formatCandle(seconds: Long): String {
        val m = seconds / 60L
        val s = seconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun ColorForLabel(color: Int, dark: Boolean): Int {
        return if (!dark) color else 0xFF8FBFFF.toInt()
    }

    private fun drawSessionBand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rr: Float,
        width: Float,
        session: Session,
        enabled: Boolean,
        now: ZonedDateTime
    ) {
        ringTrack.color = if (darkTheme) 0xFF39414D.toInt() else 0xFFDCE3EB.toInt()
        ringTrack.strokeWidth = width
        ringTrack.alpha = if (darkTheme) 150 else 125
        canvas.drawCircle(cx, cy, rr, ringTrack)

        val local = sessionLocalInterval(session, now)
        sessionPaint.color = session.color
        sessionPaint.strokeWidth = width
        sessionPaint.alpha = if (enabled) 225 else 42

        val startAngle = local.first * 15f - 90f
        var sweep = (local.second - local.first) * 15f
        if (sweep < 0f) sweep += 360f
        canvas.drawArc(
            RectF(cx - rr, cy - rr, cx + rr, cy + rr),
            startAngle,
            sweep.coerceAtMost(359.9f),
            false,
            sessionPaint
        )
    }

    private fun sessionLocalInterval(session: Session, now: ZonedDateTime): Pair<Float, Float> {
        val localZone = ZoneId.systemDefault()
        val sourceDate = now.withZoneSameInstant(ZoneId.of(session.zone)).toLocalDate()
        val zone = ZoneId.of(session.zone)
        val startSource = ZonedDateTime.of(sourceDate, LocalTime.of(session.startHour, 0), zone)
        val endDate = if (session.endHour <= session.startHour) sourceDate.plusDays(1) else sourceDate
        val endSource = ZonedDateTime.of(endDate, LocalTime.of(session.endHour, 0), zone)
        val startLocal = startSource.withZoneSameInstant(localZone)
        val endLocal = endSource.withZoneSameInstant(localZone)
        return Pair(
            startLocal.hour + startLocal.minute / 60f,
            endLocal.hour + endLocal.minute / 60f
        )
    }

    private fun midpointAngle(start: Float, end: Float): Float {
        val span = if (end >= start) end - start else end + 24f - start
        var mid = start + span / 2f
        while (mid >= 24f) mid -= 24f
        return mid
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val cx = width / 2f
        val cy = height / 2f
        val dx = event.x - cx
        val dy = event.y - cy
        val distance = sqrt(dx * dx + dy * dy)
        val size = min(width, height).toFloat()
        val radius = (size / 2f - 24f).coerceAtLeast(1f)
        val bandBase = radius * 0.76f
        val bandGap = radius * 0.115f
        val bandWidth = radius * 0.090f

        var hitIndex = -1
        var hitDistance = Float.MAX_VALUE
        sessions.forEachIndexed { index, _ ->
            val rr = bandBase - index * bandGap
            val d = kotlin.math.abs(distance - rr)
            if (d <= bandWidth * 1.7f && d < hitDistance) {
                hitDistance = d
                hitIndex = index
            }
        }

        if (hitIndex >= 0) {
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
            var hour = angle / 15f
            while (hour < 0f) hour += 24f
            while (hour >= 24f) hour -= 24f
            val session = sessions[hitIndex]
            val local = sessionLocalInterval(session, ZonedDateTime.now())
            if (isHourInRange(hour, local.first, local.second)) {
                onSessionToggle(session.name)
                return true
            }
        }
        return true
    }

    private fun isHourInRange(hour: Float, start: Float, end: Float): Boolean {
        return if (start <= end) hour >= start && hour < end else hour >= start || hour < end
    }

    private fun drawHand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        length: Float,
        degrees: Float,
        width: Float,
        color: Int
    ) {
        hand.color = color
        hand.strokeWidth = width
        val radians = Math.toRadians(degrees.toDouble())
        canvas.drawLine(cx, cy, cx + cos(radians).toFloat() * length, cy + sin(radians).toFloat() * length, hand)
    }
}
