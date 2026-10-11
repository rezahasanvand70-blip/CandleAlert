package com.example.candlealert

import android.content.Context
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Shared source of truth for market session hours.
 *
 * Market hours are specified in each market's civil time. For rendering, their
 * endpoints are converted to Instants (UTC timeline) and then into the chosen
 * display zone. ZoneId rules provide each region's current/future UTC offset.
 */
object TradingSessions {
    data class Session(
        val name: String,
        val zone: String,
        val startHour: Int,
        val endHour: Int,
        val color: Int
    )

    val sessions = listOf(
        Session("Sydney", "Australia/Sydney", 9, 18, 0xFF6F9C91.toInt()),
        Session("Tokyo", "Asia/Tokyo", 9, 18, 0xFF91A9B4.toInt()),
        Session("Frankfurt", "Europe/Berlin", 8, 17, 0xFFC2A56C.toInt()),
        Session("London", "Europe/London", 8, 17, 0xFF4F8D82.toInt()),
        Session("New York", "America/New_York", 8, 17, 0xFF176B63.toInt())
    )

    /**
     * An absent override means follow the phone's time zone automatically.
     * No location permission is needed; this reads Android's configured zone.
     */
    fun displayZone(context: Context): ZoneId {
        val override = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("clock_timezone", null)
            ?.trim()
            .orEmpty()
        return if (override.isBlank()) {
            ZoneId.systemDefault()
        } else {
            runCatching { ZoneId.of(override) }.getOrElse { ZoneId.systemDefault() }
        }
    }

    fun localInterval(session: Session, reference: Instant, displayZone: ZoneId): Pair<Float, Float> {
        val marketZone = ZoneId.of(session.zone)
        val marketDate = reference.atZone(marketZone).toLocalDate()
        val startAtMarket = ZonedDateTime.of(marketDate, LocalTime.of(session.startHour, 0), marketZone)
        val endDate = if (session.endHour <= session.startHour) marketDate.plusDays(1) else marketDate
        val endAtMarket = ZonedDateTime.of(endDate, LocalTime.of(session.endHour, 0), marketZone)

        // An Instant is an absolute UTC point, independent of either wall clock.
        val startDisplay = startAtMarket.toInstant().atZone(displayZone)
        val endDisplay = endAtMarket.toInstant().atZone(displayZone)
        return Pair(
            startDisplay.hour + startDisplay.minute / 60f,
            endDisplay.hour + endDisplay.minute / 60f
        )
    }

    fun isOpen(name: String, instant: Instant): Boolean {
        val session = sessions.firstOrNull { it.name == name } ?: return false
        val marketTime = instant.atZone(ZoneId.of(session.zone))
        val minuteOfDay = marketTime.hour * 60 + marketTime.minute
        val start = session.startHour * 60
        val end = session.endHour * 60
        return if (start < end) minuteOfDay in start until end
        else minuteOfDay >= start || minuteOfDay < end
    }

    fun zoneLabel(zone: ZoneId, instant: Instant = Instant.now()): String {
        val offset = zone.rules.getOffset(instant)
        val offsetLabel = if (offset.totalSeconds == 0) "UTC±00:00" else "UTC${offset.id}"
        return "${zone.id} ($offsetLabel)"
    }
}
