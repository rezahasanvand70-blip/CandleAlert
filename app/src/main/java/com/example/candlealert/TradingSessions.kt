package com.example.candlealert

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Session-hour convention reference: BabyPips Forex Market Hours.
 * https://www.babypips.com/tools/forex-market-hours
 * Failed or malformed source responses never overwrite the last known schedule.
 */
object TradingSessions {
    data class Session(val name: String, val zone: String, var startHour: Int, var endHour: Int, val color: Int)

    private const val SOURCE_URL = "https://www.babypips.com/tools/forex-market-hours"
    private const val PREFS = "prefs"
    private const val CHECKED_AT = "session_source_checked_at"
    private const val NOTICE_PENDING = "session_source_notice"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var refreshRunning = false

    val sessions = mutableListOf(
        Session("Sydney", "Australia/Sydney", 9, 18, 0xFFF97372.toInt()),
        Session("Tokyo", "Asia/Tokyo", 9, 18, 0xFF8B5CF6.toInt()),
        Session("Frankfurt", "Europe/Berlin", 8, 17, 0xFF3B82F6.toInt()),
        Session("London", "Europe/London", 8, 17, 0xFF14B8A6.toInt()),
        Session("New York", "America/New_York", 8, 17, 0xFFF59E0B.toInt())
    )

    fun refreshFromSource(context: Context, callback: ((String?) -> Unit)? = null) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong(CHECKED_AT, 0L) < CHECK_INTERVAL_MS) {
            callback?.invoke(null)
            return
        }
        synchronized(this) {
            if (refreshRunning) return
            refreshRunning = true
        }
        executor.execute {
            var notice: String? = null
            try {
                val connection = URL(SOURCE_URL).openConnection() as HttpURLConnection
                val html = try {
                    connection.connectTimeout = 10000
                    connection.readTimeout = 12000
                    connection.setRequestProperty("User-Agent", "CandleAlert/1.4")
                    if (connection.responseCode !in 200..299) error("HTTP error")
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally { connection.disconnect() }

                val parsed = parsePublishedLocalHours(html)
                require(parsed.keys.containsAll(setOf("Sydney", "Tokyo", "London", "New York"))) {
                    "Source schedule incomplete"
                }
                val changed = mutableListOf<String>()
                synchronized(sessions) {
                    for (session in sessions) {
                        val hours = parsed[session.name] ?: continue
                        require(hours.first in 0..23 && hours.second in 1..24 && hours.first != hours.second)
                        val oldStart = prefs.getInt("session_${session.name}_start", session.startHour)
                        val oldEnd = prefs.getInt("session_${session.name}_end", session.endHour)
                        if (oldStart != hours.first || oldEnd != hours.second) {
                            changed += "${session.name}: ${formatHour(oldStart)}–${formatHour(oldEnd)} → ${formatHour(hours.first)}–${formatHour(hours.second)}"
                        }
                        session.startHour = hours.first
                        session.endHour = hours.second
                    }
                }
                val edit = prefs.edit().putLong(CHECKED_AT, System.currentTimeMillis())
                parsed.forEach { (name, hours) ->
                    edit.putInt("session_${name}_start", hours.first)
                    edit.putInt("session_${name}_end", hours.second)
                }
                if (changed.isNotEmpty()) {
                    notice = "برنامهٔ ساعت سشن‌ها از منبع BabyPips به‌روزرسانی شد.\n\n" +
                        changed.joinToString("\n") + "\n\nمنبع: BabyPips Forex Market Hours"
                    edit.putString(NOTICE_PENDING, notice)
                }
                edit.apply()
            } catch (_: Exception) {
                // Offline, blocked, or unexpected source format: retain last known schedule.
            } finally {
                synchronized(this) { refreshRunning = false }
                callback?.let { cb -> mainHandler.post { cb(notice) } }
            }
        }
    }

    fun consumePendingNotice(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(NOTICE_PENDING, null)
        if (value != null) prefs.edit().remove(NOTICE_PENDING).apply()
        return value
    }

    private fun parsePublishedLocalHours(html: String): Map<String, Pair<Int, Int>> {
        val result = linkedMapOf<String, Pair<Int, Int>>()
        val rows = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>")
        val cellsRegex = Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>")
        val tags = Regex("(?is)<[^>]+>")
        for (row in rows.findAll(html)) {
            val cells = cellsRegex.findAll(row.groupValues[1]).map {
                it.groupValues[1].replace(tags, " ")
                    .replace("&nbsp;", " ").replace("&#8211;", "–").replace("&ndash;", "–")
                    .replace("&#8212;", "—").replace("&mdash;", "—")
                    .replace(Regex("\\s+"), " ").trim()
            }.toList()
            if (cells.size < 2 || cells[0] !in setOf("Sydney", "Tokyo", "London", "New York")) continue
            val time = cells[1].replace('—', '–').replace('-', '–')
            val m = Regex("(?i)(\\d{1,2})(?::\\d{2})?\\s*(am|pm)\\s*–\\s*(\\d{1,2})(?::\\d{2})?\\s*(am|pm)").find(time) ?: continue
            fun hour(n: String, ap: String): Int {
                val h = n.toInt()
                require(h in 1..12)
                return h % 12 + if (ap.equals("pm", true)) 12 else 0
            }
            result.putIfAbsent(cells[0], Pair(hour(m.groupValues[1], m.groupValues[2]), hour(m.groupValues[3], m.groupValues[4])))
        }
        return result
    }

    private fun formatHour(hour: Int) = "%02d:00".format(Locale.US, hour % 24)

    fun displayZone(context: Context): ZoneId {
        val override = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("clock_timezone", null)?.trim().orEmpty()
        return if (override.isBlank()) ZoneId.systemDefault() else runCatching { ZoneId.of(override) }.getOrElse { ZoneId.systemDefault() }
    }

    fun localInterval(session: Session, reference: Instant, displayZone: ZoneId): Pair<Float, Float> {
        val marketZone = ZoneId.of(session.zone)
        val marketDate = reference.atZone(marketZone).toLocalDate()
        val start = ZonedDateTime.of(marketDate, LocalTime.of(session.startHour, 0), marketZone)
        val endDate = if (session.endHour <= session.startHour) marketDate.plusDays(1) else marketDate
        val end = ZonedDateTime.of(endDate, LocalTime.of(session.endHour % 24, 0), marketZone)
        val a = start.toInstant().atZone(displayZone)
        val b = end.toInstant().atZone(displayZone)
        return Pair(a.hour + a.minute / 60f, b.hour + b.minute / 60f)
    }

    fun isOpen(name: String, instant: Instant): Boolean {
        val session = sessions.firstOrNull { it.name == name } ?: return false
        val time = instant.atZone(ZoneId.of(session.zone))
        if (time.dayOfWeek == DayOfWeek.SATURDAY || time.dayOfWeek == DayOfWeek.SUNDAY) return false
        val current = time.hour * 60 + time.minute
        val start = session.startHour * 60
        val end = session.endHour * 60
        return if (start < end) current in start until end else current >= start || current < end
    }

    fun zoneLabel(zone: ZoneId, instant: Instant = Instant.now()): String {
        val offset = zone.rules.getOffset(instant)
        val label = if (offset.totalSeconds == 0) "UTC±00:00" else "UTC${offset.id}"
        return "${zone.id} ($label)"
    }
}
