package com.example.candlealert

import android.app.*
import android.content.*
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        val p = c.getSharedPreferences("prefs", 0)
        val now = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault()).format(Date())

        val old = p.getStringSet("history", emptySet())?.toMutableSet() ?: mutableSetOf()
        old.add(now)
        while (old.size > 20) old.remove(old.minOrNull())
        p.edit().putStringSet("history", old).apply()

        val nm = c.getSystemService(NotificationManager::class.java)
        val channelId = "candle_alerts_v2"

        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "Candle alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Candle close alerts"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        // Use a guaranteed system notification icon. A malformed custom icon
        // can prevent the notification from being posted on some Android builds.
        val notification = NotificationCompat.Builder(c, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("CandleAlert")
            .setContentText("Candle close alert • Check your setup")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        if (Build.VERSION.SDK_INT < 33 || nm.areNotificationsEnabled()) {
            nm.notify(1001, notification)
        }

        Scheduler.scheduleNext(c)
    }
}
