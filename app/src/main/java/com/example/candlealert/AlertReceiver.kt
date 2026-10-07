package com.example.candlealert

import android.app.*
import android.content.*
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        val p=c.getSharedPreferences("prefs",0)
        val now=SimpleDateFormat("yyyy-MM-dd  HH:mm",Locale.getDefault()).format(Date())
        val old=p.getStringSet("history",emptySet())?.toMutableSet()?:mutableSetOf()
        old.add(now)
        while(old.size>20) old.remove(old.minOrNull())
        p.edit().putStringSet("history",old).apply()

        val nm=c.getSystemService(NotificationManager::class.java)
        val ch="candle"
        if(android.os.Build.VERSION.SDK_INT>=26){
            nm.createNotificationChannel(NotificationChannel(ch,"Candle alerts",NotificationManager.IMPORTANCE_HIGH).apply{
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),null)
            })
        }
        nm.notify(1001,NotificationCompat.Builder(c,ch)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("CandleAlert")
            .setContentText("Candle close alert • Check your setup")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build())
        Scheduler.scheduleNext(c)
    }
}