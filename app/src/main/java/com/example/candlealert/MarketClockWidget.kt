package com.example.candlealert

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MarketClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val zone = TradingSessions.displayZone(context)
            val views = RemoteViews(context.packageName, R.layout.widget_market_clock)
            views.setString(R.id.widget_clock, "setTimeZone", zone.id)
            views.setTextViewText(R.id.widget_zone, TradingSessions.zoneLabel(zone))
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pending)
            views.setOnClickPendingIntent(R.id.widget_zone, pending)
            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, MarketClockWidget::class.java)
            onUpdate(context, manager, manager.getAppWidgetIds(component))
        }
    }
}