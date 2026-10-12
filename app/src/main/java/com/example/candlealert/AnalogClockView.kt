package com.example.candlealert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.graphics.Canvas
import android.graphics.Color
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
    private val mapBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mapGridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mapFramePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerLeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerLabelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        markerLabelTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        markerLabelTextPaint.textAlign = Paint.Align.CENTER
        markerHaloPaint.style = Paint.Style.FILL
        markerLabelBgPaint.style = Paint.Style.FILL
        markerLabelBorderPaint.style = Paint.Style.STROKE
        mapGridPaint.style = Paint.Style.STROKE
        mapFramePaint.style = Paint.Style.STROKE
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

        drawPixelWorldMap(canvas, cx, cy, radius * 0.84f, now)

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
        number.textSize = radius * 0.067f
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

        drawHand(canvas, cx, cy, radius * 0.34f, hourFloat24 * 15f - 90f, u(7f), if (darkTheme) 0xFFE9EEE7.toInt() else 0xFF183E38.toInt())
        drawHand(canvas, cx, cy, radius * 0.55f, minuteFloat * 6f - 90f, u(4.5f), if (darkTheme) 0xFFE9EEE7.toInt() else 0xFF183E38.toInt())
        drawHand(canvas, cx, cy, radius * 0.64f, secondFloat * 6f - 90f, u(2f), 0xFFC5A15A.toInt())
        // A defined center pivot visually joins the three hands and keeps their intersection crisp.
        center.style = Paint.Style.FILL
        center.color = if (darkTheme) 0xFF0E1B1A.toInt() else 0xFFFFFEFA.toInt()
        canvas.drawCircle(cx, cy, u(8f), center)
        center.color = accent
        canvas.drawCircle(cx, cy, u(4.1f), center)
        center.color = if (darkTheme) 0xFFE9EEE7.toInt() else 0xFF183E38.toInt()
        canvas.drawCircle(cx, cy, u(1.5f), center)

        // Foreground markers stay above the clock hands so the financial sessions remain readable.
        drawMarketMarkers(canvas, cx, cy, radius * 0.84f, sessions, selected, now)

        // Digital time and candle countdown are shown in the compact strip below the dial.

        // Country labels are kept outside the map to preserve the clean pixel-globe look.


        postInvalidateDelayed(1000)
    }


    private data class GeoPoint(val lon: Float, val lat: Float)

    // Simplified country silhouettes in geographic coordinates; the pixel grid keeps the map crisp at every size.
    private val worldMapMask: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
        val bytes = Base64.decode(WORLD_MASK_BASE64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("World map mask could not be decoded")
    }
    private val smoothWorldMask: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
        Bitmap.createScaledBitmap(worldMapMask, 720, 360, true)
    }
    private var cachedWorldMap: Bitmap? = null
    private var cachedWorldMapMinuteKey: Long = Long.MIN_VALUE

    // Equirectangular projection: the full world is displayed at its correct 2:1 aspect ratio.
    // This keeps all five financial-session cities visible at once.
    private fun project(point: GeoPoint, cx: Float, cy: Float, r: Float): Pair<Float, Float> =
        Pair(cx + point.lon / 180f * r, cy - point.lat / 90f * r * 0.5f)

    private fun markerOffset(name: String): Pair<Float, Float> = when (name) {
        // These two cities are geographically close and need a small visual separation.
        "London" -> Pair(-u(7f), -u(4f))
        "Frankfurt" -> Pair(u(7f), u(5f))
        else -> Pair(0f, 0f)
    }

    private fun markerPosition(session: TradingSessions.Session, cx: Float, cy: Float, r: Float): Pair<Float, Float> {
        val point = project(sessionLocation(session.name), cx, cy, r)
        val offset = markerOffset(session.name)
        return Pair(point.first + offset.first, point.second + offset.second)
    }

    private fun markerCode(name: String): String = when (name) {
        "New York" -> "NY"
        "London" -> "LDN"
        "Frankfurt" -> "FRA"
        "Tokyo" -> "TYO"
        "Sydney" -> "SYD"
        else -> name.take(3).uppercase(Locale.ROOT)
    }

    private fun markerLabelOffset(name: String): Pair<Float, Float> = when (name) {
        "New York" -> Pair(u(13f), -u(8f))
        "London" -> Pair(-u(24f), -u(8f))
        "Frankfurt" -> Pair(u(18f), u(13f))
        "Tokyo" -> Pair(-u(23f), -u(8f))
        "Sydney" -> Pair(-u(25f), u(13f))
        else -> Pair(u(10f), -u(8f))
    }

    private fun sessionLocation(name: String): GeoPoint = when (name.lowercase(Locale.ROOT)) {
        "new york" -> GeoPoint(-74.0f, 40.7f)
        "london" -> GeoPoint(-0.12f, 51.5f)
        "frankfurt" -> GeoPoint(8.68f, 50.1f)
        "tokyo" -> GeoPoint(139.7f, 35.7f)
        "sydney" -> GeoPoint(151.2f, -33.9f)
        else -> GeoPoint(0f, 0f)
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

    private fun worldMapForMinute(instant: Instant): Bitmap {
        val minuteKey = instant.epochSecond / 60L
        cachedWorldMap?.let { if (cachedWorldMapMinuteKey == minuteKey) return it }

        val width = 720
        val height = 360
        val mask = smoothWorldMask
        val maskPixels = IntArray(width * height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val solar = solarPosition(instant)
        val sinDeclination = sin(solar.declinationRadians)
        val cosDeclination = cos(solar.declinationRadians)
        val subSolar = Math.toRadians(solar.subsolarLongitude)
        val sinSubSolar = sin(subSolar)
        val cosSubSolar = cos(subSolar)

        val dayOcean = if (darkTheme) 0xFF294C52.toInt() else 0xFFE7F0E8.toInt()
        val nightOcean = if (darkTheme) 0xFF142734.toInt() else 0xFF9BAEB7.toInt()
        val dayLand = if (darkTheme) 0xFF8DB8A0.toInt() else 0xFF729B7A.toInt()
        val nightLand = if (darkTheme) 0xFF31545A.toInt() else 0xFF526F7B.toInt()
        val twilightOcean = if (darkTheme) 0xFF203D47.toInt() else 0xFFC5C8B6.toInt()
        val twilightLand = if (darkTheme) 0xFF527A76.toInt() else 0xFF9AAE9A.toInt()

        val sinLatitudes = DoubleArray(height)
        val cosLatitudes = DoubleArray(height)
        for (y in 0 until height) {
            val latitude = Math.toRadians(90.0 - y * 180.0 / (height - 1))
            sinLatitudes[y] = sin(latitude)
            cosLatitudes[y] = cos(latitude)
        }
        val sinLongitudes = DoubleArray(width)
        val cosLongitudes = DoubleArray(width)
        for (x in 0 until width) {
            val longitude = Math.toRadians(-180.0 + x * 360.0 / (width - 1))
            sinLongitudes[x] = sin(longitude)
            cosLongitudes[x] = cos(longitude)
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val sinLat = sinLatitudes[y]
            val cosLat = cosLatitudes[y]
            val row = y * width
            for (x in 0 until width) {
                val sunElevation = sinLat * sinDeclination +
                    cosLat * cosDeclination *
                    (cosLongitudes[x] * cosSubSolar + sinLongitudes[x] * sinSubSolar)

                // The mask has antialiased edges. Blend land and ocean colors instead of
                // thresholding the coastline into a jagged collection of squares.
                val landAmount = Color.red(maskPixels[row + x]) / 255f
                val dayColor = blendColor(dayOcean, dayLand, landAmount)
                val nightColor = blendColor(nightOcean, nightLand, landAmount)
                val twilightColor = blendColor(twilightOcean, twilightLand, landAmount)
                pixels[row + x] = when {
                    sunElevation >= 0.104528 -> dayColor
                    sunElevation >= 0.0 -> blendColor(twilightColor, dayColor, (sunElevation / 0.104528).toFloat())
                    sunElevation > -0.104528 -> blendColor(nightColor, twilightColor, ((sunElevation + 0.104528) / 0.104528).toFloat())
                    else -> nightColor
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        cachedWorldMap = result
        cachedWorldMapMinuteKey = minuteKey
        return result
    }

    private fun drawPixelWorldMap(canvas: Canvas, cx: Float, cy: Float, r: Float, now: ZonedDateTime) {
        val mapRect = RectF(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.5f)
        val corner = u(5f)
        val clip = Path().apply { addRoundRect(mapRect, corner, corner, Path.Direction.CW) }

        // A clean, antialiased map panel replaces the old square-by-square drawing.
        mapFramePaint.style = Paint.Style.FILL
        mapFramePaint.color = if (darkTheme) 0xFF13242A.toInt() else 0xFFE8EFEB.toInt()
        canvas.drawRoundRect(mapRect, corner, corner, mapFramePaint)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(worldMapForMinute(now.toInstant()), null, mapRect, mapBitmapPaint)

        // Restrained graticule lines add geographic structure without obscuring coastlines.
        mapGridPaint.color = if (darkTheme) 0x28FFFFFF else 0x28344742
        mapGridPaint.strokeWidth = u(0.55f)
        for (longitude in -120..120 step 60) {
            val x = mapRect.left + (longitude + 180f) / 360f * mapRect.width()
            canvas.drawLine(x, mapRect.top, x, mapRect.bottom, mapGridPaint)
        }
        for (latitude in -60..60 step 30) {
            val y = mapRect.top + (90f - latitude) / 180f * mapRect.height()
            canvas.drawLine(mapRect.left, y, mapRect.right, y, mapGridPaint)
        }
        canvas.restore()

        mapFramePaint.style = Paint.Style.STROKE
        mapFramePaint.strokeWidth = u(0.8f)
        mapFramePaint.color = if (darkTheme) 0x80597576.toInt() else 0x66FFFFFF
        canvas.drawRoundRect(mapRect, corner, corner, mapFramePaint)
    }

    private fun drawMarketMarkers(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        mapRadius: Float,
        sessions: List<TradingSessions.Session>,
        selected: Set<String>,
        now: ZonedDateTime
    ) {
        for (session in sessions) {
            val geographicPoint = project(sessionLocation(session.name), cx, cy, mapRadius)
            val marker = markerPosition(session, cx, cy, mapRadius)
            val offset = markerOffset(session.name)
            val isOpen = TradingSessions.isOpen(session.name, now.toInstant())
            val alertsEnabled = selected.contains(session.name)

            if (offset.first != 0f || offset.second != 0f) {
                markerLeaderPaint.color = session.color
                markerLeaderPaint.alpha = 170
                markerLeaderPaint.strokeWidth = u(0.9f)
                canvas.drawLine(geographicPoint.first, geographicPoint.second, marker.first, marker.second, markerLeaderPaint)
            }

            markerHaloPaint.color = session.color
            markerHaloPaint.alpha = if (isOpen) 75 else 18
            canvas.drawCircle(marker.first, marker.second, if (isOpen) u(8.5f) else u(6f), markerHaloPaint)

            sessionPaint.style = Paint.Style.FILL
            sessionPaint.color = session.color
            sessionPaint.alpha = if (isOpen) 255 else 175
            canvas.drawCircle(marker.first, marker.second, if (isOpen) u(4.1f) else u(3.6f), sessionPaint)

            // A white outline shows alert selection; the colored fill/glow independently
            // communicates market-open status. These are deliberately two different states.
            sessionPaint.style = Paint.Style.STROKE
            sessionPaint.color = if (alertsEnabled) Color.WHITE else session.color
            sessionPaint.strokeWidth = if (alertsEnabled) u(1.35f) else u(0.9f)
            sessionPaint.alpha = if (alertsEnabled) 245 else 160
            canvas.drawCircle(marker.first, marker.second, if (alertsEnabled) u(5.3f) else u(4.4f), sessionPaint)

            val code = markerCode(session.name)
            markerLabelTextPaint.textSize = u(8.2f)
            markerLabelTextPaint.color = if (darkTheme) 0xFFF4F7F4.toInt() else 0xFF203631.toInt()
            val labelOffset = markerLabelOffset(session.name)
            val labelCenterX = marker.first + labelOffset.first
            val labelCenterY = marker.second + labelOffset.second
            val pillWidth = markerLabelTextPaint.measureText(code) + u(8f)
            val pillHeight = u(15f)
            val labelRect = RectF(
                labelCenterX - pillWidth / 2f,
                labelCenterY - pillHeight / 2f,
                labelCenterX + pillWidth / 2f,
                labelCenterY + pillHeight / 2f
            )
            markerLabelBgPaint.color = if (darkTheme) 0xE61A2B31.toInt() else 0xEFFFFFFF.toInt()
            canvas.drawRoundRect(labelRect, u(4f), u(4f), markerLabelBgPaint)
            markerLabelBorderPaint.color = session.color
            markerLabelBorderPaint.strokeWidth = u(0.8f)
            markerLabelBorderPaint.alpha = if (isOpen) 255 else 150
            canvas.drawRoundRect(labelRect, u(4f), u(4f), markerLabelBorderPaint)
            val baseline = labelCenterY - (markerLabelTextPaint.ascent() + markerLabelTextPaint.descent()) / 2f
            canvas.drawText(code, labelCenterX, baseline, markerLabelTextPaint)
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
        // Keep hit-test geometry exactly aligned with the dial and visible marker positions.
        val radius = (size / 2f - u(22f)).coerceAtLeast(u(1f))
        val mapRadius = radius * 0.84f
        val hitRadius = u(22f)
        val hit = TradingSessions.sessions.map { session ->
            val p = markerPosition(session, cx, cy, mapRadius)
            val dx = event.x - p.first
            val dy = event.y - p.second
            session to (dx * dx + dy * dy)
        }.filter { it.second <= hitRadius * hitRadius }
            .minByOrNull { it.second }
        if (hit != null) onSessionToggle(hit.first.name)
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
    companion object {
        // Public-domain Natural Earth low-resolution land mask, excluding Antarctica.
        private const val WORLD_MASK_BASE64 = "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"
    }

}
