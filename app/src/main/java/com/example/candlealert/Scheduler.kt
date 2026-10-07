package com.example.candlealert

import android.app.*
import android.content.*
import android.os.Build
import java.time.*
import java.util.concurrent.Executors

object Scheduler {
    private val executor = Executors.newSingleThreadExecutor()

    fun scheduleNext(c: Context) {
        val app = c.applicationContext
        executor.execute { scheduleNextInternal(app) }
    }

    private fun scheduleNextInternal(c: Context) {
        val p = c.getSharedPreferences("prefs", 0)
        val am = c.getSystemService(AlarmManager::class.java)
        val i = Intent(c, AlertReceiver::class.java)
        val pi = PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        if (!p.getBoolean("enabled", true)) return

        val tf = p.getInt("tf", 5).coerceAtLeast(1)
        val mode = p.getInt("mode", 0)
        val off = p.getInt("offset", 0).coerceAtLeast(0)
        val now = Instant.now().epochSecond
        val period = tf * 60L

        // Start from the next candle close and keep advancing until we find
        // a trigger that is actually in the future and allowed by the filters.
        var close = ((now / period) + 1) * period
        var attempts = 0
        while (attempts++ < 10000) {
            val trigger = when (mode) {
                0 -> close - off
                2 -> close + off
                else -> close
            }

            if (trigger > now) {
                val z = ZonedDateTime.ofInstant(Instant.ofEpochSecond(trigger), ZoneId.systemDefault())
                val quiet = inQuiet(z.toLocalDateTime(), p.getString("quiet", "00:00-07:30") ?: "00:00-07:30")
                val open = marketOpen(trigger, c)

                if (!quiet && open) {
                    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
                    runCatching {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            trigger * 1000,
                            pi
                        )
                    }
                    return
                }
            }

            close += period
        }
    }

    private fun marketOpen(epoch: Long, c: Context): Boolean {
        val p = c.getSharedPreferences("prefs", 0)
        val market = p.getInt("market", 0)
        val z = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
        val dow = z.dayOfWeek

        if ((market == 0 || market == 2) &&
            (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)
        ) return false

        if (market == 1) return true

        val selected = p.getStringSet(
            "sessions",
            setOf("Sydney", "Tokyo", "Frankfurt", "London", "New York")
        ) ?: emptySet()

        if (selected.isEmpty()) return false
        return selected.any { sessionOpen(it, z.toInstant()) }
    }

    private fun sessionOpen(s: String, instant: Instant): Boolean {
        val zone = when (s) {
            "Sydney" -> "Australia/Sydney"
            "Tokyo" -> "Asia/Tokyo"
            "Frankfurt" -> "Europe/Berlin"
            "London" -> "Europe/London"
            else -> "America/New_York"
        }

        val t = ZonedDateTime.ofInstant(instant, ZoneId.of(zone))
        val m = t.hour * 60 + t.minute

        val ranges = mapOf(
            "Sydney" to (22 * 60 to 7 * 60),
            "Tokyo" to (0 to 9 * 60),
            "Frankfurt" to (7 * 60 to 16 * 60),
            "London" to (8 * 60 to 17 * 60),
            "New York" to (13 * 60 to 22 * 60)
        )

        val r = ranges[s] ?: return false
        return if (r.first < r.second) {
            m in r.first until r.second
        } else {
            m >= r.first || m < r.second
        }
    }

    private fun inQuiet(t: LocalDateTime, r: String): Boolean {
        val a = r.split("-")
        if (a.size != 2) return false

        fun x(v: String): Int {
            val p = v.trim().split(":")
            if (p.size != 2) return -1
            return p[0].toIntOrNull()?.times(60)?.plus(p[1].toIntOrNull() ?: 0) ?: -1
        }

        val s = x(a[0])
        val e = x(a[1])
        val m = t.hour * 60 + t.minute
        if (s < 0 || e < 0) return false

        return if (s <= e) m in s..e else m >= s || m <= e
    }
}
