package com.example.candlealert

import android.app.*
import android.content.*
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val ch = "candle"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(ch, "Candle alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                }
            )
        }
        nm.notify(
            1001,
            NotificationCompat.Builder(c, ch)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Candle Alert")
                .setContentText("Candle alert")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
        Scheduler.scheduleNext(c)
    }
}
