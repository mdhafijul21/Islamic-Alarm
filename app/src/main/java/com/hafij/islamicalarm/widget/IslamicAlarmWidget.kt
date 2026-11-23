package com.hafij.islamicalarm.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.prayertimes.CalculationMethod
import com.hafij.islamicalarm.prayertimes.District
import com.hafij.islamicalarm.prayertimes.HijriCalendarHelper
import com.hafij.islamicalarm.prayertimes.LocationData
import com.hafij.islamicalarm.prayertimes.LocationType
import com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator
import java.util.Calendar

class IslamicAlarmWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> refreshAllWidgets(context)
            ACTION_WIDGET_PINNED -> {
                refreshAllWidgets(context)
                Toast.makeText(
                    context,
                    "✅ টাইম কাউন্টার হোম স্ক্রিনে যোগ হয়েছে",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.hafij.islamicalarm.ACTION_REFRESH_WIDGET"
        const val ACTION_WIDGET_PINNED = "com.hafij.islamicalarm.ACTION_WIDGET_PINNED"

        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, IslamicAlarmWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun loadSavedDistrict(context: Context): District {
            val prefs = context.getSharedPreferences("islamic_alarm_app_prefs", Context.MODE_PRIVATE)
            val savedNameBn = prefs.getString("selected_district_bn", null) ?: return LocationData.getDefaultDistrict()
            val lat = prefs.getFloat("selected_district_lat", 0f).toDouble()
            val lng = prefs.getFloat("selected_district_lng", 0f).toDouble()
            val tz = prefs.getFloat("selected_district_tz", 6f).toDouble()
            val offset = prefs.getInt("selected_district_offset", 0)
            val nameEn = prefs.getString("selected_district_en", "") ?: ""
            val parentBn = prefs.getString("selected_district_parent", "") ?: ""
            val type = try {
                LocationType.valueOf(prefs.getString("selected_district_type", LocationType.DISTRICT.name) ?: LocationType.DISTRICT.name)
            } catch (_: Exception) {
                LocationType.DISTRICT
            }
            val method = try {
                CalculationMethod.valueOf(prefs.getString("selected_district_method", CalculationMethod.KARACHI_BD.name) ?: CalculationMethod.KARACHI_BD.name)
            } catch (_: Exception) {
                CalculationMethod.KARACHI_BD
            }
            return if (lat != 0.0 && lng != 0.0) {
                District(
                    nameEn = nameEn,
                    nameBn = savedNameBn,
                    divisionBn = parentBn,
                    lat = lat,
                    lng = lng,
                    offsetMinutes = offset,
                    timeZone = tz,
                    type = type,
                    parentBn = parentBn,
                    method = method
                )
            } else {
                LocationData.findDistrict(savedNameBn)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_islamic_alarm)

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

            val refreshIntent = Intent(context, IslamicAlarmWidget::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPendingIntent)

            val now = Calendar.getInstance()
            val district = loadSavedDistrict(context)
            val schedule = PrayerTimeCalculator.calculate(now, district)
            val nextPrayer = PrayerTimeCalculator.getNextPrayer(now, schedule)
            val hijri = HijriCalendarHelper.getHijriDate(now)

            val cleanLocation = district.displayName.removePrefix("📍 ").trim()
            views.setTextViewText(R.id.tvWidgetLocation, "$cleanLocation • ${hijri.formattedBn}")
            views.setTextViewText(
                R.id.tvWidgetNextPrayer,
                "পরবর্তী নামাজ: ${nextPrayer.prayerNameBn} (${nextPrayer.prayerTimeStr})"
            )

            val remainingMillis = nextPrayer.remainingMillis.coerceAtLeast(1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                views.setChronometerCountDown(R.id.chronometerWidgetCountdown, true)
            }
            views.setChronometer(
                R.id.chronometerWidgetCountdown,
                SystemClock.elapsedRealtime() + remainingMillis,
                "বাকি %s",
                true
            )

            views.setTextViewText(
                R.id.tvWidgetSehriIftar,
                "সাহরি শেষ: ${schedule.sehriEnd} • ইফতার: ${schedule.maghrib}"
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
            scheduleNextWaqtRefresh(context, remainingMillis)
        }

        private fun scheduleNextWaqtRefresh(context: Context, remainingMillis: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val refreshIntent = Intent(context, IslamicAlarmWidget::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    404,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = System.currentTimeMillis() + remainingMillis + 2000L
                alarmManager.set(AlarmManager.RTC, triggerAt, pending)
            } catch (_: Exception) {
            }
        }
    }
}
