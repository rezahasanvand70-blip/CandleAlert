package com.example.candlealert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.time.*
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
    private val timeframeMinutesProvider: () -> Int,
    private val onSessionToggle: (String) -> Unit
) : View(context) {
    private fun u(v: Float): Float = v * resources.displayMetrics.density


    private data class Session(val name: String, val zone: String, val startHour: Int, val endHour: Int, val color: Int)

    private val sessions = listOf(
        Session("Sydney", "Australia/Sydney", 8, 17, 0xFF6F9C91.toInt()),
        Session("Tokyo", "Asia/Tokyo", 9, 18, 0xFF91A9B4.toInt()),
        Session("Frankfurt", "Europe/Berlin", 8, 17, 0xFFC2A56C.toInt()),
        Session("London", "Europe/London", 8, 17, 0xFF4F8D82.toInt()),
        Session("New York", "America/New_York", 8, 17, 0xFF176B63.toInt())
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
        val radius = (size / 2f - u(22f)).coerceAtLeast(u(1f))
        val prefs = context.getSharedPreferences("prefs", 0)
        val selected = prefs.getStringSet("sessions", sessions.map { it.name }.toSet()) ?: emptySet()
        val deviceZone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(deviceZone)

        face.style = Paint.Style.FILL
        face.color = if (darkTheme) 0xFF132321.toInt() else 0xFFFFFEFA.toInt()
        canvas.drawCircle(cx, cy, radius + u(13f), face)
        face.style = Paint.Style.STROKE
        face.strokeWidth = u(1.5f)
        face.color = if (darkTheme) 0xFF49635E.toInt() else 0xFFD8DED5.toInt()
        canvas.drawCircle(cx, cy, radius + u(13f), face)
        face.style = Paint.Style.FILL
        face.color = if (darkTheme) 0xFF1B302D.toInt() else 0xFFF1F2E9.toInt()
        canvas.drawCircle(cx, cy, radius, face)

        val candle = candleState()
        candlePaint.strokeWidth = radius * 0.035f
        candlePaint.color = if (darkTheme) 0xFF49635E.toInt() else 0xFFD7DED5.toInt()
        candlePaint.alpha = 220
        canvas.drawCircle(cx, cy, radius + u(7f), candlePaint)
        candlePaint.color = accent
        candlePaint.alpha = 235
        val progressSweep = (candle.progress * 360f).coerceIn(0f, 359.9f)
        canvas.drawArc(RectF(cx - radius - u(7f), cy - radius - u(7f), cx + radius + u(7f), cy + radius + u(7f)), -90f, progressSweep, false, candlePaint)

        // Replace the radial session bands with a pixel world map.
        drawPixelWorldMap(canvas, cx, cy, radius * 1.30f, radius * 0.66f, selected, now)

        // Hour markers are small dots placed outside the numerals, so they never cross the 1–24 labels.
        tick.style = Paint.Style.FILL
        for (h in 0 until 24) {
            val angle = Math.toRadians(h * 15.0 - 90.0)
            val markerRadius = radius * 0.972f
            tick.color = primary
            tick.alpha = if (h % 3 == 0) 190 else 85
            val dotRadius = if (h % 3 == 0) u(2.0f) else u(1.25f)
            canvas.drawCircle(
                cx + cos(angle).toFloat() * markerRadius,
                cy + sin(angle).toFloat() * markerRadius,
                dotRadius,
                tick
            )
        }
        tick.style = Paint.Style.STROKE

        number.textAlign = Paint.Align.CENTER
        number.textSize = radius * 0.058f
        number.color = primary
        number.alpha = 225
        for (h in 0 until 24 step 3) {
            val angle = Math.toRadians(h * 15.0 - 90.0)
            val nr = radius * 0.900f
            canvas.drawText(h.toString().padStart(2, '0'), cx + cos(angle).toFloat() * nr, cy + sin(angle).toFloat() * nr - (number.ascent() + number.descent()) / 2f, number)
        }

        val hour = now.hour
        val minute = now.minute
        val second = now.second
        val millis = now.nano / 1_000_000
        val secondFloat = second + millis / 1000f
        val minuteFloat = minute + secondFloat / 60f
        val hourFloat24 = hour + minuteFloat / 60f

        drawHand(canvas, cx, cy, radius * 0.24f, hourFloat24 * 15f - 90f, u(7f), if (darkTheme) 0xFFE9EEE7.toInt() else 0xFF183E38.toInt())
        drawHand(canvas, cx, cy, radius * 0.39f, minuteFloat * 6f - 90f, u(4.5f), if (darkTheme) 0xFFE9EEE7.toInt() else 0xFF183E38.toInt())
        drawHand(canvas, cx, cy, radius * 0.47f, secondFloat * 6f - 90f, u(2f), 0xFFC5A15A.toInt())

        // Digital time and candle countdown are shown in the compact strip below the dial.

        // Session activity is represented by highlighted country borders on the map.

        postInvalidateDelayed(1000)
    }

    private data class CandleState(val progress: Float, val remainingSeconds: Long)

    private fun candleState(): CandleState {
        val tf = timeframeMinutesProvider().coerceAtLeast(1)
        val deviceZone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(deviceZone)
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

    private data class GeoPoint(val lon: Double, val lat: Double)

    private val landRegions = listOf(
        listOf(GeoPoint(-168.0, 65.0), GeoPoint(-140.0, 70.0), GeoPoint(-112.0, 58.0), GeoPoint(-95.0, 50.0), GeoPoint(-82.0, 25.0), GeoPoint(-105.0, 15.0), GeoPoint(-117.0, 32.0), GeoPoint(-130.0, 50.0), GeoPoint(-155.0, 55.0)),
        listOf(GeoPoint(-82.0, 12.0), GeoPoint(-68.0, 8.0), GeoPoint(-50.0, -5.0), GeoPoint(-35.0, -10.0), GeoPoint(-48.0, -35.0), GeoPoint(-70.0, -55.0), GeoPoint(-78.0, -25.0)),
        listOf(GeoPoint(-12.0, 36.0), GeoPoint(8.0, 37.0), GeoPoint(35.0, 30.0), GeoPoint(50.0, 12.0), GeoPoint(42.0, -12.0), GeoPoint(28.0, -35.0), GeoPoint(14.0, -35.0), GeoPoint(-2.0, 5.0), GeoPoint(-17.0, 15.0)),
        listOf(GeoPoint(-10.0, 36.0), GeoPoint(2.0, 50.0), GeoPoint(18.0, 58.0), GeoPoint(38.0, 68.0), GeoPoint(65.0, 55.0), GeoPoint(95.0, 58.0), GeoPoint(130.0, 50.0), GeoPoint(160.0, 60.0), GeoPoint(178.0, 48.0), GeoPoint(150.0, 35.0), GeoPoint(122.0, 20.0), GeoPoint(105.0, 5.0), GeoPoint(78.0, 8.0), GeoPoint(58.0, 25.0), GeoPoint(42.0, 38.0), GeoPoint(28.0, 36.0), GeoPoint(15.0, 45.0), GeoPoint(5.0, 36.0)),
        listOf(GeoPoint(112.0, -11.0), GeoPoint(154.0, -10.0), GeoPoint(153.0, -39.0), GeoPoint(132.0, -44.0), GeoPoint(114.0, -33.0)),
        listOf(GeoPoint(95.0, 5.0), GeoPoint(130.0, 5.0), GeoPoint(140.0, -8.0), GeoPoint(112.0, -10.0)),
        listOf(GeoPoint(-52.0, 60.0), GeoPoint(-42.0, 82.0), GeoPoint(-20.0, 78.0), GeoPoint(-28.0, 60.0))
    )

    private val sessionCountries = mapOf(
        "Sydney" to listOf(GeoPoint(113.0, -11.0), GeoPoint(154.0, -11.0), GeoPoint(153.0, -39.0), GeoPoint(145.0, -43.0), GeoPoint(132.0, -35.0), GeoPoint(114.0, -22.0)),
        "Tokyo" to listOf(GeoPoint(129.0, 31.0), GeoPoint(132.0, 34.0), GeoPoint(136.0, 35.0), GeoPoint(141.0, 40.0), GeoPoint(145.0, 44.0), GeoPoint(141.0, 46.0), GeoPoint(137.0, 39.0), GeoPoint(132.0, 36.0)),
        "Frankfurt" to listOf(GeoPoint(5.5, 55.0), GeoPoint(14.8, 54.8), GeoPoint(15.0, 50.7), GeoPoint(12.0, 47.3), GeoPoint(7.0, 47.4), GeoPoint(5.5, 50.0)),
        "London" to listOf(GeoPoint(-6.5, 58.5), GeoPoint(1.7, 58.0), GeoPoint(1.5, 50.0), GeoPoint(-5.5, 50.0), GeoPoint(-6.5, 54.0)),
        "New York" to listOf(GeoPoint(-124.0, 48.5), GeoPoint(-117.0, 32.5), GeoPoint(-106.0, 31.8), GeoPoint(-97.0, 26.0), GeoPoint(-81.0, 25.0), GeoPoint(-80.0, 32.0), GeoPoint(-67.0, 45.0), GeoPoint(-83.0, 47.5), GeoPoint(-95.0, 49.0))
    )

    private fun drawPixelWorldMap(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        mapWidth: Float,
        mapHeight: Float,
        selected: Set<String>,
        now: ZonedDateTime
    ) {
        val left = cx - mapWidth / 2f
        val top = cy - mapHeight / 2f
        val cols = 48
        val rows = 22
        val cellW = mapWidth / cols
        val cellH = mapHeight / rows
        val gap = maxOf(u(0.45f), cellW * 0.14f)
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        val utcHour = utc.hour + utc.minute / 60.0
        val dayOfYear = utc.dayOfYear
        val declination = Math.toRadians(23.44 * sin(2.0 * Math.PI * (dayOfYear - 81) / 365.0))
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val pixel = RectF()
        val clip = Path().apply { addCircle(cx, cy, mapWidth * 0.53f, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(clip)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val lon = -180.0 + (col + 0.5) * 360.0 / cols
                val lat = 82.0 - (row + 0.5) * 142.0 / rows
                val point = GeoPoint(lon, lat)
                if (landRegions.none { pointInPolygon(point, it) }) continue
                val hourAngle = Math.toRadians((utcHour + lon / 15.0 - 12.0) * 15.0)
                val latitude = Math.toRadians(lat)
                val sunHeight = sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle)
                val night = sunHeight < -0.03
                dot.color = when {
                    night && darkTheme -> 0xFF284653.toInt()
                    night -> 0xFF71868B.toInt()
                    darkTheme -> 0xFF65D6C0.toInt()
                    else -> 0xFF287D73.toInt()
                }
                dot.alpha = if (night) 155 else 230
                val x = left + col * cellW
                val y = top + row * cellH
                pixel.set(x + gap / 2f, y + gap / 2f, x + cellW - gap / 2f, y + cellH - gap / 2f)
                canvas.drawRect(pixel, dot)
            }
        }

        // Draw glowing outlines for enabled market countries; the borders are intentionally
        // simplified at this pixel-map scale to remain legible behind the clock hands.
        selected.forEach { name ->
            val polygon = sessionCountries[name] ?: return@forEach
            val path = Path()
            polygon.forEachIndexed { index, p ->
                val x = left + ((p.lon + 180.0) / 360.0 * mapWidth).toFloat()
                val y = top + ((82.0 - p.lat) / 142.0 * mapHeight).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = u(3.4f)
                strokeJoin = Paint.Join.ROUND
                color = sessions.firstOrNull { it.name == name }?.color ?: accent
                alpha = 55
            }
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = u(1.25f)
                strokeJoin = Paint.Join.ROUND
                color = sessions.firstOrNull { it.name == name }?.color ?: accent
                alpha = 255
            }
            canvas.drawPath(path, glow)
            canvas.drawPath(path, border)
        }
        canvas.restoreToCount(save)

        // Subtle dotted equator accent gives the map a crisp pixel-art finish.
        dot.color = if (darkTheme) 0xFF9ACBC1.toInt() else 0xFF548E84.toInt()
        dot.alpha = 65
        for (i in 0 until cols step 3) {
            val x = left + (i + 0.5f) * cellW
            canvas.drawCircle(x, cy, u(0.45f), dot)
        }
    }

    private fun pointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val crosses = (a.lat > point.lat) != (b.lat > point.lat) &&
                point.lon < (b.lon - a.lon) * (point.lat - a.lat) / ((b.lat - a.lat).takeIf { it != 0.0 } ?: 1e-9) + a.lon
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, length: Float, degrees: Float, width: Float, color: Int) {
        hand.color = color
        hand.strokeWidth = width
        val radians = Math.toRadians(degrees.toDouble())
        canvas.drawLine(cx, cy, cx + cos(radians).toFloat() * length, cy + sin(radians).toFloat() * length, hand)
    }
}
