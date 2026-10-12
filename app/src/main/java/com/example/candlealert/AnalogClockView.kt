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
    // More recognizable world silhouettes, with major peninsulas and islands separated.
    private fun continents(): List<List<GeoPoint>> = listOf(
        listOf(GeoPoint(-168f,66f),GeoPoint(-155f,71f),GeoPoint(-137f,60f),GeoPoint(-128f,54f),GeoPoint(-124f,48f),GeoPoint(-124f,40f),GeoPoint(-117f,32f),GeoPoint(-108f,23f),GeoPoint(-98f,17f),GeoPoint(-83f,9f),GeoPoint(-77f,18f),GeoPoint(-81f,25f),GeoPoint(-97f,29f),GeoPoint(-105f,42f),GeoPoint(-119f,50f),GeoPoint(-130f,55f),GeoPoint(-145f,60f),GeoPoint(-160f,60f)),
        listOf(GeoPoint(-53f,83f),GeoPoint(-27f,77f),GeoPoint(-20f,68f),GeoPoint(-43f,59f),GeoPoint(-58f,65f),GeoPoint(-66f,74f)),
        listOf(GeoPoint(-97f,18f),GeoPoint(-88f,21f),GeoPoint(-82f,15f),GeoPoint(-77f,8f),GeoPoint(-83f,7f),GeoPoint(-91f,14f)),
        listOf(GeoPoint(-81f,12f),GeoPoint(-70f,10f),GeoPoint(-60f,6f),GeoPoint(-50f,-2f),GeoPoint(-35f,-8f),GeoPoint(-40f,-22f),GeoPoint(-50f,-35f),GeoPoint(-68f,-56f),GeoPoint(-75f,-40f),GeoPoint(-78f,-20f)),
        listOf(GeoPoint(-10f,36f),GeoPoint(-10f,44f),GeoPoint(-9f,54f),GeoPoint(-5f,58f),GeoPoint(3f,60f),GeoPoint(15f,70f),GeoPoint(32f,71f),GeoPoint(48f,60f),GeoPoint(60f,65f),GeoPoint(80f,72f),GeoPoint(105f,76f),GeoPoint(130f,70f),GeoPoint(160f,60f),GeoPoint(179f,60f),GeoPoint(160f,50f),GeoPoint(145f,40f),GeoPoint(130f,30f),GeoPoint(120f,20f),GeoPoint(108f,10f),GeoPoint(100f,5f),GeoPoint(80f,8f),GeoPoint(70f,20f),GeoPoint(60f,25f),GeoPoint(50f,30f),GeoPoint(40f,40f),GeoPoint(30f,30f),GeoPoint(20f,30f),GeoPoint(10f,30f),GeoPoint(0f,35f)),
        listOf(GeoPoint(35f,32f),GeoPoint(50f,30f),GeoPoint(56f,20f),GeoPoint(40f,12f),GeoPoint(30f,18f)),
        listOf(GeoPoint(-17f,37f),GeoPoint(0f,36f),GeoPoint(12f,33f),GeoPoint(32f,31f),GeoPoint(43f,12f),GeoPoint(51f,11f),GeoPoint(40f,-5f),GeoPoint(32f,-20f),GeoPoint(20f,-35f),GeoPoint(10f,-34f),GeoPoint(-1f,-20f),GeoPoint(-12f,5f),GeoPoint(-17f,20f)),
        listOf(GeoPoint(68f,24f),GeoPoint(88f,28f),GeoPoint(93f,20f),GeoPoint(105f,8f),GeoPoint(98f,1f),GeoPoint(80f,7f),GeoPoint(70f,15f)),
        listOf(GeoPoint(130f,33f),GeoPoint(135f,30f),GeoPoint(142f,38f),GeoPoint(145f,44f),GeoPoint(140f,45f),GeoPoint(134f,38f)),
        listOf(GeoPoint(95f,5f),GeoPoint(110f,0f),GeoPoint(120f,-7f),GeoPoint(130f,-5f),GeoPoint(125f,2f),GeoPoint(115f,8f),GeoPoint(105f,8f)),
        listOf(GeoPoint(120f,18f),GeoPoint(126f,8f),GeoPoint(123f,5f),GeoPoint(119f,12f)),
        listOf(GeoPoint(113f,-11f),GeoPoint(130f,-12f),GeoPoint(145f,-15f),GeoPoint(154f,-28f),GeoPoint(145f,-39f),GeoPoint(131f,-35f),GeoPoint(115f,-33f)),
        listOf(GeoPoint(47f,-13f),GeoPoint(50f,-17f),GeoPoint(49f,-25f),GeoPoint(44f,-22f))
    )

    private fun project(point: GeoPoint, cx: Float, cy: Float, r: Float): Pair<Float, Float> =
        Pair(cx + point.lon / 180f * r, cy - point.lat / 90f * r)

    private fun screenToGeo(x: Float, y: Float, cx: Float, cy: Float, r: Float): Pair<Float, Float> =
        Pair(((x - cx) / r * 180f).coerceIn(-180f, 180f), (-(y - cy) / r * 90f).coerceIn(-90f, 90f))

    private fun sessionLocation(name: String): GeoPoint = when (name.lowercase(Locale.ROOT)) {
        "new york" -> GeoPoint(-74.0f, 40.7f)
        "london" -> GeoPoint(-0.12f, 51.5f)
        "frankfurt" -> GeoPoint(8.68f, 50.1f)
        "tokyo" -> GeoPoint(139.7f, 35.7f)
        "sydney" -> GeoPoint(151.2f, -33.9f)
        else -> GeoPoint(0f, 0f)
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

    private data class SolarPosition(val subsolarLongitude: Double, val declinationRadians: Double)

    /**
     * NOAA-style solar coordinates from the actual UTC instant.
     * Equation of time corrects the difference between clock time and apparent solar time;
     * declination accounts for Earth's axial tilt and the date of the year.
     */
    private fun solarPosition(instant: Instant): SolarPosition {
        val julianDay = instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val t = (julianDay - 2_451_545.0) / 36_525.0
        val geomMeanLong = ((280.46646 + t * (36_000.76983 + t * 0.0003032)) % 360.0 + 360.0) % 360.0
        val geomMeanAnomaly = Math.toRadians(357.52911 + t * (35_999.05029 - 0.0001537 * t))
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val equationCenter = sin(geomMeanAnomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2.0 * geomMeanAnomaly) * (0.019993 - 0.000101 * t) +
            sin(3.0 * geomMeanAnomaly) * 0.000289
        val trueLong = geomMeanLong + equationCenter
        val omega = 125.04 - 1934.136 * t
        val apparentLong = Math.toRadians(trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega)))
        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(Math.toRadians(omega)))
        val declination = kotlin.math.asin(sin(obliquity) * sin(apparentLong))

        val y = kotlin.math.tan(obliquity / 2.0) * kotlin.math.tan(obliquity / 2.0)
        val l0 = Math.toRadians(geomMeanLong)
        val equationOfTime = 4.0 * Math.toDegrees(
            y * sin(2.0 * l0) - 2.0 * eccentricity * sin(geomMeanAnomaly) +
                4.0 * eccentricity * y * sin(geomMeanAnomaly) * cos(2.0 * l0) -
                0.5 * y * y * sin(4.0 * l0) -
                1.25 * eccentricity * eccentricity * sin(2.0 * geomMeanAnomaly)
        )
        val utcMinutes = instant.atZone(ZoneOffset.UTC).let { it.hour * 60.0 + it.minute + it.second / 60.0 }
        var subsolarLon = 180.0 - (utcMinutes + equationOfTime) / 4.0
        while (subsolarLon > 180.0) subsolarLon -= 360.0
        while (subsolarLon < -180.0) subsolarLon += 360.0
        return SolarPosition(subsolarLon, declination)
    }

    private fun blendColor(a: Int, b: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val inv = 1f - t
        val red = (((a shr 16) and 0xFF) * inv + ((b shr 16) and 0xFF) * t).toInt()
        val green = (((a shr 8) and 0xFF) * inv + ((b shr 8) and 0xFF) * t).toInt()
        val blue = ((a and 0xFF) * inv + (b and 0xFF) * t).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun drawPixelWorldMap(canvas: Canvas, cx: Float, cy: Float, r: Float, sessions: List<TradingSessions.Session>, selected: Set<String>, now: ZonedDateTime) {
        val dayOcean = if (darkTheme) 0xFF294C52.toInt() else 0xFFE7F0E8.toInt()
        val nightOcean = if (darkTheme) 0xFF142734.toInt() else 0xFF9BAEB7.toInt()
        val dayLand = if (darkTheme) 0xFF8DB8A0.toInt() else 0xFF729B7A.toInt()
        val nightLand = if (darkTheme) 0xFF31545A.toInt() else 0xFF526F7B.toInt()
        val twilightOcean = if (darkTheme) 0xFF203D47.toInt() else 0xFFC5C8B6.toInt()
        val twilightLand = if (darkTheme) 0xFF527A76.toInt() else 0xFF9AAE9A.toInt()
        val solar = solarPosition(now.toInstant())
        val landShapes = continents()
        face.style = Paint.Style.FILL

        // Fine square pixels keep coastlines recognizable at phone-clock scale.
        var lat = -78.0
        while (lat <= 78.0) {
            var lon = -178.2
            while (lon <= 178.2) {
                val point = GeoPoint(lon.toFloat(), lat.toFloat())
                val p = project(point, cx, cy, r)
                val dx = p.first - cx
                val dy = p.second - cy
                if (dx * dx + dy * dy <= r * r) {
                    val isLand = landShapes.any { pointInPolygon(point.lon, point.lat, it) }
                    val latitudeRad = Math.toRadians(lat)
                    val longitudeDelta = Math.toRadians(lon - solar.subsolarLongitude)
                    val sunElevationCosine = sin(latitudeRad) * sin(solar.declinationRadians) +
                        cos(latitudeRad) * cos(solar.declinationRadians) * cos(longitudeDelta)
                    val dayColor = if (isLand) dayLand else dayOcean
                    val nightColor = if (isLand) nightLand else nightOcean
                    val color = when {
                        sunElevationCosine >= 0.0 -> dayColor
                        sunElevationCosine <= -0.104528 -> nightColor
                        else -> blendColor(nightColor, if (isLand) twilightLand else twilightOcean,
                            ((sunElevationCosine + 0.104528) / 0.104528).toFloat())
                    }
                    face.color = color
                    face.alpha = if (isLand) 255 else 225
                    val dotX = r * 0.0105f
                    val dotY = r * 0.0052f
                    canvas.drawRect(p.first - dotX, p.second - dotY, p.first + dotX, p.second + dotY, face)
                }
                lon += 3.6
            }
            lat += 1.8
        }

        // Show actual financial-session cities instead of misleading country-sized rectangles.
        sessions.forEach { session ->
            val xy = project(sessionLocation(session.name), cx, cy, r)
            val enabled = selected.contains(session.name)
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = session.color
                alpha = if (enabled) 52 else 18
            }
            val markerRadius = if (enabled) u(7.5f) else u(5.5f)
            canvas.drawCircle(xy.first, xy.second, markerRadius * 1.8f, halo)
            sessionPaint.style = Paint.Style.STROKE
            sessionPaint.color = session.color
            sessionPaint.strokeWidth = u(1.25f)
            sessionPaint.alpha = if (enabled) 255 else 115
            canvas.drawCircle(xy.first, xy.second, markerRadius, sessionPaint)
            sessionPaint.style = Paint.Style.FILL
            sessionPaint.color = session.color
            sessionPaint.alpha = if (enabled) 255 else 150
            canvas.drawCircle(xy.first, xy.second, u(2.5f), sessionPaint)
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
        val size = min(width, height).toFloat()
        val radius = (size / 2f - u(24f)).coerceAtLeast(u(1f))
        val mapRadius = radius * 0.84f
        val hitRadius = u(22f)
        val hit = TradingSessions.sessions.map { session ->
            val p = project(sessionLocation(session.name), cx, cy, mapRadius)
            Triple(session, p.first, p.second)
        }.map { (session, x, y) ->
            val dx = event.x - x
            val dy = event.y - y
            Triple(session, x, y) to (dx * dx + dy * dy)
        }.filter { it.second <= hitRadius * hitRadius }
            .minByOrNull { it.second }
        if (hit != null) onSessionToggle(hit.first.first.name)
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
