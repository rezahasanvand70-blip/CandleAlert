package com.example.candlealert

import android.content.*

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        Scheduler.scheduleNext(c)
    }
}
