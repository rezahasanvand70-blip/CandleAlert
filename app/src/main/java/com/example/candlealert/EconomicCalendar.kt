package com.example.candlealert

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

data class EconomicEvent(
    val id: String,
    val title: String,
    val country: String,
    val currency: String,
    val impact: String,
    val timeUtc: String,
    val category: String,
    val consensus: String,
    val previous: String,
    val actual: String,
    val url: String
) {
    fun epochMillis(): Long? = runCatching { Instant.parse(timeUtc).toEpochMilli() }.getOrNull()
    fun displayTime(zone: ZoneId): String {
        val instant = runCatching { Instant.parse(timeUtc) }.getOrNull() ?: return "Time not available"
        return DateTimeFormatter.ofPattern("EEE, dd MMM • HH:mm", Locale.getDefault()).withZone(zone).format(instant)
    }
    fun alarmKey(): String = id
}

object EconomicCalendarRepository {
    private const val BASE = "https://www.financecalendar.com/wp-json/fc/v1/calendar"

    fun load(from: LocalDate, to: LocalDate, callback: (Result<List<EconomicEvent>>) -> Unit) {
        thread(name = "economic-calendar") {
            val result = runCatching {
                require(!to.isBefore(from)) { "Invalid date range" }
                val url = URL("$BASE?from=$from&to=$to&limit=500")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "CandleAlert-Android")
                }
                try {
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    check(status in 200..299) { "Calendar service returned HTTP $status" }
                    parse(body)
                } finally {
                    connection.disconnect()
                }
            }
            callback(result)
        }
    }

    private fun parse(body: String): List<EconomicEvent> {
        val trimmed = body.trim()
        val rootArray = if (trimmed.startsWith("[")) JSONArray(trimmed) else null
        val root = if (rootArray == null) JSONObject(body) else JSONObject()
        val events = rootArray ?: when {
            root.optJSONArray("events") != null -> root.optJSONArray("events")!!
            root.optJSONArray("data") != null -> root.optJSONArray("data")!!
            root.optJSONArray("calendar") != null -> root.optJSONArray("calendar")!!
            else -> JSONArray()
        }
        return (0 until events.length()).mapNotNull { index ->
            val o = events.optJSONObject(index) ?: return@mapNotNull null
            val time = first(o, "time_utc", "datetime_utc", "date_utc", "datetime", "date", "release_time")
            val normalizedTime = normalizeTime(time) ?: return@mapNotNull null
            val title = first(o, "title", "name", "event", "event_name", "report_name")
            if (title.isBlank()) return@mapNotNull null
            val impactRaw = first(o, "impact", "importance", "impact_level").lowercase(Locale.US)
            val impact = when (impactRaw) {
                "3", "high", "red", "high impact" -> "high"
                "2", "medium", "moderate", "orange", "medium impact" -> "medium"
                "1", "low", "green", "low impact" -> "low"
                else -> impactRaw.ifBlank { "unknown" }
            }
            // Keep ISO country codes separate from currency codes. The previous
            // parser treated iso_country_code as a currency, which confused region
            // classification whenever a feed supplied only country metadata.
            val country = first(
                o, "country", "country_name", "country_code", "countryCode",
                "iso_country_code", "isoCountryCode", "region", "region_name"
            )
            val currency = first(
                o, "currency", "currency_code", "currencyCode", "iso_currency_code",
                "isoCurrencyCode", "ccy", "currency_symbol"
            )
            val url = first(o, "url", "source_url", "link").ifBlank { "https://www.financecalendar.com/" }
            val id = first(o, "id", "event_id", "eventId", "slug")
                .ifBlank { "${normalizedTime}_${title}_${country}_${currency}" }
            EconomicEvent(
                id = id,
                title = title,
                country = country.ifBlank { "Global / not specified" },
                currency = currency,
                impact = impact,
                timeUtc = normalizedTime,
                category = first(o, "category", "series", "type"),
                consensus = first(o, "consensus", "forecast", "expected"),
                previous = first(o, "prior", "previous", "previous_value"),
                actual = first(o, "actual", "actual_value"),
                url = url
            )
        }.distinctBy { it.id }.sortedBy { it.epochMillis() ?: Long.MAX_VALUE }
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = o.opt(key)
            if (value != null && value != JSONObject.NULL) {
                val s = value.toString().trim()
                if (s.isNotBlank() && s != "null") return s
            }
        }
        return ""
    }

    private fun normalizeTime(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching { Instant.parse(raw).toString() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(raw).toInstant().toString() }.getOrNull()
            ?: runCatching {
                java.time.LocalDateTime.parse(raw.replace(" ", "T")).atZone(ZoneId.of("UTC")).toInstant().toString()
            }.getOrNull()
    }
}

object EconomicAlertScheduler {
    private const val PREFS_KEY = "economic_alerts"

    fun schedule(context: Context, event: EconomicEvent, minutesBefore: Int): Boolean {
        val release = event.epochMillis() ?: return false
        val triggerAt = release - minutesBefore.coerceAtLeast(0) * 60_000L
        if (triggerAt <= System.currentTimeMillis()) return false
        val key = event.id
        val saved = JSONObject().apply {
            put("id", event.id)
            put("title", event.title)
            put("country", event.country)
            put("currency", event.currency)
            put("impact", event.impact)
            put("timeUtc", event.timeUtc)
            put("minutesBefore", minutesBefore)
        }.toString()
        val prefs = context.getSharedPreferences("prefs", 0)
        val alarms = prefs.getStringSet(PREFS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        alarms.removeAll { runCatching { JSONObject(it).optString("id") == key }.getOrDefault(false) }
        alarms.add(saved)
        prefs.edit().putStringSet(PREFS_KEY, alarms).apply()
        return setAlarm(context, key, event.title, event.country, triggerAt, minutesBefore)
    }

    fun cancel(context: Context, eventId: String) {
        val alarms = context.getSharedPreferences("prefs", 0).getStringSet(PREFS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        alarms.removeAll { runCatching { JSONObject(it).optString("id") == eventId }.getOrDefault(false) }
        context.getSharedPreferences("prefs", 0).edit().putStringSet(PREFS_KEY, alarms).apply()
        val pi = pendingIntent(context, eventId, "", "", 0)
        context.getSystemService(AlarmManager::class.java).cancel(pi)
    }

    fun hasAlarm(context: Context, eventId: String): Boolean {
        val alarms = context.getSharedPreferences("prefs", 0).getStringSet(PREFS_KEY, emptySet()) ?: emptySet()
        return alarms.any { runCatching { JSONObject(it).optString("id") == eventId }.getOrDefault(false) }
    }

    fun rescheduleAll(context: Context) {
        val prefs = context.getSharedPreferences("prefs", 0)
        val alarms = prefs.getStringSet(PREFS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val keep = mutableSetOf<String>()
        alarms.forEach { raw ->
            runCatching {
                val o = JSONObject(raw)
                val id = o.optString("id")
                val title = o.optString("title")
                val country = o.optString("country")
                val time = Instant.parse(o.optString("timeUtc")).toEpochMilli()
                val before = o.optInt("minutesBefore")
                val trigger = time - before * 60_000L
                if (trigger > System.currentTimeMillis()) {
                    if (setAlarm(context, id, title, country, trigger, before)) keep.add(raw)
                }
            }
        }
        prefs.edit().putStringSet(PREFS_KEY, keep).apply()
    }

    private fun setAlarm(context: Context, id: String, title: String, country: String, trigger: Long, before: Int): Boolean {
        return runCatching {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val pi = pendingIntent(context, id, title, country, before)
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            true
        }.getOrDefault(false)
    }

    private fun pendingIntent(context: Context, id: String, title: String, country: String, before: Int): PendingIntent {
        val requestCode = id.hashCode()
        val intent = Intent(context, EconomicAlertReceiver::class.java).apply {
            putExtra("event_id", id)
            putExtra("event_title", title)
            putExtra("event_country", country)
            putExtra("minutes_before", before)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class EconomicAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra("event_title").orEmpty().ifBlank { "Economic release" }
        val country = intent?.getStringExtra("event_country").orEmpty()
        val before = intent?.getIntExtra("minutes_before", 0) ?: 0
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "economic_calendar_alerts"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Economic calendar", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders before scheduled economic releases"
            })
        }
        val open = PendingIntent.getActivity(context, 8101, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val lead = if (before == 0) "Event is scheduled now" else "Event starts in $before min"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(listOf(country, lead).filter { it.isNotBlank() }.joinToString(" • "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(country, lead).filter { it.isNotBlank() }.joinToString(" • ")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT < 33 || manager.areNotificationsEnabled()) manager.notify(intent?.getStringExtra("event_id").orEmpty().hashCode(), notification)
        val id = intent?.getStringExtra("event_id").orEmpty()
        if (id.isNotBlank()) {
            val alarms = context.getSharedPreferences("prefs", 0).getStringSet("economic_alerts", emptySet())?.toMutableSet() ?: mutableSetOf()
            alarms.removeAll { runCatching { JSONObject(it).optString("id") == id }.getOrDefault(false) }
            context.getSharedPreferences("prefs", 0).edit().putStringSet("economic_alerts", alarms).apply()
        }
    }
}
