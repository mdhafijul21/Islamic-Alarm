package com.hafij.islamicalarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.data.AlarmStore
import java.util.Calendar
import java.util.Locale

class IslamicAlarmWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, IslamicAlarmWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.hafij.islamicalarm.ACTION_REFRESH_WIDGET"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_islamic_alarm)

            // Open MainActivity on tap
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

            // Refresh button action
            val refreshIntent = Intent(context, IslamicAlarmWidget::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPendingIntent)

            // Fetch next active alarm
            val alarmStore = AlarmStore(context)
            val enabledAlarms = alarmStore.getAlarms().filter { it.isEnabled }

            if (enabledAlarms.isNotEmpty()) {
                val nextAlarm = enabledAlarms.first()
                val formattedTime = String.format(
                    Locale.getDefault(),
                    "%02d:%02d %s (%s)",
                    if (nextAlarm.hour % 12 == 0) 12 else nextAlarm.hour % 12,
                    nextAlarm.minute,
                    if (nextAlarm.hour >= 12) "PM" else "AM",
                    if (nextAlarm.label.isNotBlank()) nextAlarm.label else "এলার্ম"
                )
                views.setTextViewText(R.id.tvWidgetNextPrayer, "পরবর্তী এলার্ম: $formattedTime")
            } else {
                views.setTextViewText(R.id.tvWidgetNextPrayer, "পরবর্তী এলার্ম: সক্রিয় এলার্ম নেই")
            }

            views.setTextViewText(R.id.tvWidgetSehriIftar, "সাহরী ০৪:২০ AM | ইফতার ০৬:৩০ PM")

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
