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
    private fun u(v: Float): Float = v * resources.displayMetrics.density


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
        val sessions = TradingSessions.sessions
        val selected = prefs.getStringSet("sessions", sessions.map { it.name }.toSet()) ?: emptySet()
        val displayZone = TradingSessions.displayZone(context)
        val now = ZonedDateTime.now(displayZone)

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

        drawPixelWorldMap(canvas, cx, cy, radius * 0.84f, sessions, selected, now)

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

        // Country labels are kept outside the map to preserve the clean pixel-globe look.


        postInvalidateDelayed(1000)
    }


    private data class GeoPoint(val lon: Float, val lat: Float)

    // Simplified country silhouettes in geographic coordinates; the pixel grid keeps the map crisp at every size.
    private fun countryPolygon(name: String): List<GeoPoint> = when (name.lowercase(Locale.ROOT)) {
        "new york" -> listOf(GeoPoint(-125f, 49f), GeoPoint(-123f, 42f), GeoPoint(-117f, 32f), GeoPoint(-108f, 31f), GeoPoint(-97f, 26f), GeoPoint(-82f, 25f), GeoPoint(-80f, 32f), GeoPoint(-67f, 45f), GeoPoint(-82f, 49f))
        "london" -> listOf(GeoPoint(-8f, 59f), GeoPoint(-6f, 50f), GeoPoint(2f, 50f), GeoPoint(2f, 59f))
        "frankfurt" -> listOf(GeoPoint(5f, 55f), GeoPoint(6f, 47f), GeoPoint(15f, 47f), GeoPoint(17f, 55f))
        "tokyo" -> listOf(GeoPoint(129f, 46f), GeoPoint(130f, 31f), GeoPoint(146f, 31f), GeoPoint(146f, 46f))
        "sydney" -> listOf(GeoPoint(112f, -10f), GeoPoint(113f, -44f), GeoPoint(154f, -44f), GeoPoint(154f, -10f))
        else -> emptyList()
    }

    private fun continents(): List<List<GeoPoint>> = listOf(
        listOf(GeoPoint(-168f, 70f), GeoPoint(-140f, 72f), GeoPoint(-115f, 55f), GeoPoint(-95f, 50f), GeoPoint(-80f, 25f), GeoPoint(-105f, 8f), GeoPoint(-120f, 20f), GeoPoint(-130f, 48f), GeoPoint(-160f, 58f)),
        listOf(GeoPoint(-80f, 12f), GeoPoint(-48f, 8f), GeoPoint(-35f, -10f), GeoPoint(-52f, -56f), GeoPoint(-72f, -35f)),
        listOf(GeoPoint(-12f, 72f), GeoPoint(35f, 70f), GeoPoint(55f, 52f), GeoPoint(38f, 35f), GeoPoint(18f, 5f), GeoPoint(-5f, -35f), GeoPoint(-18f, 0f), GeoPoint(-8f, 35f)),
        listOf(GeoPoint(30f, 70f), GeoPoint(170f, 65f), GeoPoint(145f, 45f), GeoPoint(120f, 20f), GeoPoint(80f, 5f), GeoPoint(55f, 25f), GeoPoint(40f, 45f)),
        listOf(GeoPoint(112f, -10f), GeoPoint(155f, -12f), GeoPoint(150f, -40f), GeoPoint(115f, -35f))
    )

    private fun project(point: GeoPoint, cx: Float, cy: Float, r: Float): Pair<Float, Float> {
        return Pair(cx + point.lon / 180f * r, cy - point.lat / 90f * r)
    }

    private fun screenToGeo(x: Float, y: Float, cx: Float, cy: Float, r: Float): Pair<Float, Float> {
        return Pair(((x - cx) / r * 180f).coerceIn(-180f, 180f), (-(y - cy) / r * 90f).coerceIn(-90f, 90f))
    }

    private fun pointInPolygon(lon: Float, lat: Float, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[j]
            if ((a.lat > lat) != (b.lat > lat) &&
                lon < (b.lon - a.lon) * (lat - a.lat) / ((b.lat - a.lat).takeIf { it != 0f } ?: 0.0001f) + a.lon) inside = !inside
            j = i
        }
        return inside
    }

    private fun drawPixelWorldMap(canvas: Canvas, cx: Float, cy: Float, r: Float, sessions: List<TradingSessions.Session>, selected: Set<String>, now: ZonedDateTime) {
        val stepLon = 4.5f
        val stepLat = 4.5f
        val solarLon = -now.withZoneSameInstant(ZoneOffset.UTC).hour * 15f - now.minute / 4f
        val dayColor = if (darkTheme) 0xFF385A59.toInt() else 0xFFF8F0C9.toInt()
        val nightColor = if (darkTheme) 0xFF142C3B.toInt() else 0xFF78919B.toInt()
        val landDay = if (darkTheme) 0xFF6D9D8A.toInt() else 0xFFB7C8A8.toInt()
        val landNight = if (darkTheme) 0xFF284B50.toInt() else 0xFF526F78.toInt()
        face.style = Paint.Style.FILL
        for (lat in -78..78 step stepLat.toInt()) {
            for (lon in -178..178 step stepLon.toInt()) {
                val point = GeoPoint(lon.toFloat(), lat.toFloat())
                val p = project(point, cx, cy, r)
                val dx = p.first - cx; val dy = p.second - cy
                if (dx * dx + dy * dy > r * r) continue
                val land = continents().any { pointInPolygon(point.lon, point.lat, it) }
                val localSolar = ((lon - solarLon + 540f) % 360f) - 180f
                val daylight = cos(Math.toRadians(localSolar.toDouble())).toFloat() > -sin(Math.toRadians((23.4f * sin(2.0 * Math.PI * (now.dayOfYear - 80) / 365.0)).toDouble())).toFloat()
                face.color = if (daylight) { if (land) landDay else dayColor } else { if (land) landNight else nightColor }
                face.alpha = if (land) 245 else 220
                val dot = r * 0.014f
                canvas.drawRoundRect(p.first - dot, p.second - dot, p.first + dot, p.second + dot, dot * 0.2f, dot * 0.2f, face)
            }
        }
        // Only enabled sessions receive a luminous outline, matching the user's original reference.
        sessions.forEach { session ->
            if (!selected.contains(session.name)) return@forEach
            val poly = countryPolygon(session.name)
            if (poly.size < 3) return@forEach
            val path = Path()
            poly.forEachIndexed { i, pt ->
                val xy = project(pt, cx, cy, r)
                if (i == 0) path.moveTo(xy.first, xy.second) else path.lineTo(xy.first, xy.second)
            }
            path.close()
            sessionPaint.style = Paint.Style.STROKE
            sessionPaint.strokeWidth = u(2.2f)
            sessionPaint.strokeJoin = android.graphics.Paint.Join.ROUND
            sessionPaint.color = session.color
            sessionPaint.alpha = 65
            sessionPaint.setShadowLayer(u(5f), 0f, 0f, session.color)
            canvas.drawPath(path, sessionPaint)
            sessionPaint.clearShadowLayer()
            sessionPaint.strokeWidth = u(1.25f)
            sessionPaint.alpha = 255
            canvas.drawPath(path, sessionPaint)
        }
        sessionPaint.style = Paint.Style.STROKE
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

    private fun drawSessionBand(canvas: Canvas, cx: Float, cy: Float, rr: Float, width: Float, session: TradingSessions.Session, enabled: Boolean, now: ZonedDateTime) {
        ringTrack.color = if (darkTheme) 0xFF3A514C.toInt() else 0xFFDDE3DA.toInt()
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
        canvas.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), startAngle, sweep.coerceAtMost(359.9f), false, sessionPaint)
    }

    private fun sessionLocalInterval(session: TradingSessions.Session, now: ZonedDateTime): Pair<Float, Float> {
        return TradingSessions.localInterval(session, now.toInstant(), now.zone)
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
        val radius = (size / 2f - u(24f)).coerceAtLeast(u(1f))
        val sessions = TradingSessions.sessions
        val mapRadius = radius * 0.84f
        if (distance <= mapRadius) {
            val geo = screenToGeo(event.x, event.y, cx, cy, mapRadius)
            val session = sessions.firstOrNull { countryPolygon(it.name).let { polygon -> pointInPolygon(geo.first, geo.second, polygon) } }
            if (session != null) {
                onSessionToggle(session.name)
                return true
            }
        }
        return true
    }

    private fun isHourInRange(hour: Float, start: Float, end: Float): Boolean {
        return if (start <= end) hour >= start && hour < end else hour >= start || hour < end
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, length: Float, degrees: Float, width: Float, color: Int) {
        hand.color = color
        hand.strokeWidth = width
        val radians = Math.toRadians(degrees.toDouble())
        canvas.drawLine(cx, cy, cx + cos(radians).toFloat() * length, cy + sin(radians).toFloat() * length, hand)
    }
}
