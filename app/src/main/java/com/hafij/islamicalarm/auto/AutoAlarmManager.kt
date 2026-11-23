package com.hafij.islamicalarm.auto

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.AlarmReceiver
import com.hafij.islamicalarm.prayertimes.CalculationMethod
import com.hafij.islamicalarm.prayertimes.District
import com.hafij.islamicalarm.prayertimes.LocationData
import com.hafij.islamicalarm.prayertimes.LocationType
import com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DhuhrJumuahTarget(
    val triggerAtMillis: Long,
    val label: String,
    val isFriday: Boolean
)

data class ActiveAzanWindow(
    val waqtKey: String,
    val waqtLabelBn: String,
    val dateKey: String,
    val windowStartMillis: Long,
    val windowEndMillis: Long
)

/**
 * Coordinates the "অটো" Alarm mode:
 *  1) Exact daily 20-minute lock alarm at 1:20 PM (Sat-Thu) and 12:45 PM (Friday / Jumu'ah).
 *  2) Time-windowed Azan detection for Fajr, Asr, Maghrib, and Isha (20-minute lock labeled
 *     with whichever Waqt matches the current prayer window when Azan is heard).
 */
object AutoAlarmManager {

    const val ACTION_AUTO_DHUHR_ALARM = "com.hafij.islamicalarm.ACTION_AUTO_DHUHR_ALARM"
    const val EXTRA_AUTO_LABEL = "extra_auto_label"
    const val AUTO_DURATION_MINUTES = 20

    private const val PREF_NAME = "islamic_auto_alarm_prefs"
    private const val KEY_AUTO_ENABLED = "auto_mode_enabled"
    private const val KEY_LAST_TRIGGER_PREFIX = "last_triggered_"
    private const val AUTO_DHUHR_REQ_CODE = 884201

    private const val WINDOW_BEFORE_MS = 15 * 60_000L
    private const val WINDOW_AFTER_MS = 45 * 60_000L

    fun isAutoModeEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ENABLED, false)

    fun setAutoModeEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ENABLED, enabled)
            .apply()
        syncAutoMode(context)
    }

    fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Synchronizes both the fixed 1:20 PM / 12:45 PM alarm and the microphone Azan listener service
     * according to [isAutoModeEnabled]. Returns true if listener started or is not needed.
     */
    fun syncAutoMode(context: Context, fromForeground: Boolean = true): Boolean {
        val appCtx = context.applicationContext
        if (!isAutoModeEnabled(appCtx)) {
            cancelDhuhrOrJumuahAlarm(appCtx)
            AzanListenerService.stop(appCtx)
            return true
        }

        scheduleNextDhuhrOrJumuahAlarm(appCtx)

        if (hasRecordAudioPermission(appCtx)) {
            return AzanListenerService.start(appCtx, fromForeground)
        }
        return false
    }

    fun rescheduleOnBoot(context: Context) {
        if (!isAutoModeEnabled(context)) return
        scheduleNextDhuhrOrJumuahAlarm(context)
        if (hasRecordAudioPermission(context)) {
            AzanListenerService.start(context, fromForeground = false)
        }
    }

    fun computeNextDhuhrOrJumuahTarget(now: Calendar = Calendar.getInstance()): DhuhrJumuahTarget {
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun applyTimeForDay(cal: Calendar): Boolean {
            val isFri = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
            if (isFri) {
                cal.set(Calendar.HOUR_OF_DAY, 12)
                cal.set(Calendar.MINUTE, 45)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 13)
                cal.set(Calendar.MINUTE, 20)
            }
            return isFri
        }

        var isFri = applyTimeForDay(candidate)
        if (candidate.timeInMillis <= now.timeInMillis) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
            isFri = applyTimeForDay(candidate)
        }

        val label = if (isFri) {
            "জুমার সালাত (অটো)"
        } else {
            "যোহর সালাত (অটো)"
        }
        return DhuhrJumuahTarget(
            triggerAtMillis = candidate.timeInMillis,
            label = label,
            isFriday = isFri
        )
    }

    fun scheduleNextDhuhrOrJumuahAlarm(context: Context, now: Calendar = Calendar.getInstance()): Long {
        val target = computeNextDhuhrOrJumuahTarget(now)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_AUTO_DHUHR_ALARM
            putExtra(EXTRA_AUTO_LABEL, target.label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AUTO_DHUHR_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        val showPendingIntent = PendingIntent.getActivity(
            context,
            AUTO_DHUHR_REQ_CODE + 1,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val info = AlarmManager.AlarmClockInfo(target.triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } catch (e: SecurityException) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.triggerAtMillis,
                    pendingIntent
                )
            } catch (ignored: Exception) {
            }
        }
        return target.triggerAtMillis
    }

    fun cancelDhuhrOrJumuahAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_AUTO_DHUHR_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AUTO_DHUHR_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Returns the current Azan listening window if [now] falls within
     * [waqtStart - 15 min, waqtStart + 45 min] for Fajr, Asr, Maghrib, or Isha.
     * (Dhuhr / Jumu'ah uses the fixed 1:20 PM / 12:45 PM scheduled alarm instead.)
     */
    fun findListeningWaqtWindow(
        now: Calendar,
        district: District
    ): ActiveAzanWindow? {
        val schedule = PrayerTimeCalculator.calculate(now, district)
        val nowMs = now.timeInMillis
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)

        val candidates = listOf(
            Triple("fajr", "ফজর সালাত (আজান শনাক্ত)", schedule.fajrCal.timeInMillis),
            Triple("asr", "আসর সালাত (আজান শনাক্ত)", schedule.asrCal.timeInMillis),
            Triple("maghrib", "মাগরিব সালাত (আজান শনাক্ত)", schedule.maghribCal.timeInMillis),
            Triple("isha", "ইশা সালাত (আজান শনাক্ত)", schedule.ishaCal.timeInMillis)
        )

        for ((key, label, startMs) in candidates) {
            val wStart = startMs - WINDOW_BEFORE_MS
            val wEnd = startMs + WINDOW_AFTER_MS
            if (nowMs in wStart..wEnd) {
                return ActiveAzanWindow(
                    waqtKey = key,
                    waqtLabelBn = label,
                    dateKey = dateKey,
                    windowStartMillis = wStart,
                    windowEndMillis = wEnd
                )
            }
        }
        return null
    }

    fun hasTriggeredWaqtOnDate(context: Context, waqtKey: String, dateKey: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_TRIGGER_PREFIX + waqtKey, null) == dateKey
    }

    fun markWaqtTriggeredOnDate(context: Context, waqtKey: String, dateKey: String) {
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TRIGGER_PREFIX + waqtKey, dateKey)
            .apply()
    }

    fun loadSavedDistrict(context: Context): District {
        val prefs = context.applicationContext
            .getSharedPreferences("islamic_alarm_app_prefs", Context.MODE_PRIVATE)
        val savedNameBn = prefs.getString("selected_district_bn", null) ?: return LocationData.getDefaultDistrict()
        val lat = prefs.getFloat("selected_district_lat", 0f).toDouble()
        val lng = prefs.getFloat("selected_district_lng", 0f).toDouble()
        val tz = prefs.getFloat("selected_district_tz", 6f).toDouble()
        val offset = prefs.getInt("selected_district_offset", 0)
        val nameEn = prefs.getString("selected_district_en", "") ?: ""
        val parentBn = prefs.getString("selected_district_parent", "") ?: ""
        val type = try {
            LocationType.valueOf(prefs.getString("selected_district_type", LocationType.DISTRICT.name) ?: LocationType.DISTRICT.name)
        } catch (e: Exception) {
            LocationType.DISTRICT
        }
        val method = try {
            CalculationMethod.valueOf(prefs.getString("selected_district_method", CalculationMethod.KARACHI_BD.name) ?: CalculationMethod.KARACHI_BD.name)
        } catch (e: Exception) {
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

    fun buildStatusSummary(context: Context): String {
        if (!isAutoModeEnabled(context)) {
            return "বন্ধ আছে • চালু করলে প্রতিদিন দুপুর ১:২০ ও জুমায় ১২:৪৫ (২০ মি.) এবং অন্য ৪ ওয়াক্তে আজান শুনে ২০ মি. এলার্ম বাজবে।"
        }
        val next = computeNextDhuhrOrJumuahTarget()
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val nextDayText = if (next.isFriday) "জুমা" else "যোহর"
        val micStatus = if (hasRecordAudioPermission(context)) {
            "মাইক্রোফোন আজান শনাক্ত চালু (ফজর/আসর/মাগরিব/ইশা)"
        } else {
            "মাইক্রোফোন অনুমতি না থাকায় শুধু যোহর/জুমা (${timeFmt.format(next.triggerAtMillis)}) চলবে"
        }
        return "✅ চালু আছে • পরবর্তী $nextDayText: ${timeFmt.format(next.triggerAtMillis)} (২০ মি.) • $micStatus"
    }
}
