package com.hafij.islamicalarm.auto

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

data class MaghribAutoTarget(
    val triggerAtMillis: Long,
    val label: String,
    val formattedTime: String
)

data class ActiveAzanWindow(
    val waqtKey: String,
    val waqtLabelBn: String,
    val windowStartMillis: Long,
    val windowEndMillis: Long
)

/**
 * Coordinates the "অটো" Alarm mode:
 *  1) Exact daily 20-minute lock alarm at 1:20 PM (Sat-Thu) and 12:45 PM (Friday / Jumu'ah).
 *  2) Exact daily 20-minute lock alarm at the calculated Maghrib prayer time for the user's district.
 *  3) Passive Azan listener during Asr and Isha windows, triggering a 15-minute lock alarm
 *     as soon as Azan is heard (requires RECORD_AUDIO permission).
 */
object AutoAlarmManager {

    const val ACTION_AUTO_DHUHR_ALARM = "com.hafij.islamicalarm.ACTION_AUTO_DHUHR_ALARM"
    const val ACTION_AUTO_MAGHRIB_ALARM = "com.hafij.islamicalarm.ACTION_AUTO_MAGHRIB_ALARM"
    const val EXTRA_AUTO_LABEL = "extra_auto_label"
    const val AUTO_DURATION_MINUTES = 20
    const val AZAN_DURATION_MINUTES = 15

    private const val PREF_NAME = "islamic_auto_alarm_prefs"
    private const val KEY_AUTO_ENABLED = "auto_mode_enabled"
    private const val KEY_TRIGGERED_PREFIX = "azan_triggered_"
    private const val AUTO_DHUHR_REQ_CODE = 884201
    private const val AUTO_MAGHRIB_REQ_CODE = 884203

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
        syncAutoMode(context, fromForeground = true)
    }

    fun hasRecordAudioPermission(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun dateKey(now: Calendar): String =
        "${now.get(Calendar.YEAR)}-${now.get(Calendar.MONTH) + 1}-${now.get(Calendar.DAY_OF_MONTH)}"

    fun hasTriggeredWaqtOnDate(
        context: Context,
        waqtKey: String,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_TRIGGERED_PREFIX$waqtKey", null) == dateKey(now)
    }

    fun markWaqtTriggeredOnDate(
        context: Context,
        waqtKey: String,
        now: Calendar = Calendar.getInstance()
    ) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_TRIGGERED_PREFIX$waqtKey", dateKey(now))
            .apply()
    }

    /**
     * Returns an active Azan-listening window ONLY for Asr and Isha (`[start - 15m, start + 45m]`).
     * Fajr, Dhuhr/Jumuah, and Maghrib never use microphone listening.
     */
    fun findListeningWaqtWindow(
        now: Calendar,
        district: District
    ): ActiveAzanWindow? {
        val schedule = PrayerTimeCalculator.calculate(now, district)
        val nowMs = now.timeInMillis
        val candidates = listOf(
            Triple("asr", "আসর সালাত (আজান শনাক্ত)", schedule.asrCal.timeInMillis),
            Triple("isha", "ইশা সালাত (আজান শনাক্ত)", schedule.ishaCal.timeInMillis)
        )
        for ((key, label, baseMs) in candidates) {
            val start = baseMs - 15L * 60_000L
            val end = baseMs + 45L * 60_000L
            if (nowMs in start..end) {
                return ActiveAzanWindow(
                    waqtKey = key,
                    waqtLabelBn = label,
                    windowStartMillis = start,
                    windowEndMillis = end
                )
            }
        }
        return null
    }

    /**
     * Synchronizes Dhuhr/Jumuah exact alarm, Maghrib exact alarm, and Asr/Isha Azan listener
     * according to [isAutoModeEnabled].
     */
    fun syncAutoMode(context: Context, fromForeground: Boolean = true): Boolean {
        val appCtx = context.applicationContext
        if (!isAutoModeEnabled(appCtx)) {
            cancelDhuhrOrJumuahAlarm(appCtx)
            cancelMaghribAlarm(appCtx)
            AzanListenerService.stop(appCtx)
            return true
        }

        scheduleNextDhuhrOrJumuahAlarm(appCtx)
        scheduleNextMaghribAlarm(appCtx)
        if (hasRecordAudioPermission(appCtx) &&
            (fromForeground || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        ) {
            AzanListenerService.start(appCtx)
        }
        return true
    }

    fun rescheduleOnBoot(context: Context) {
        if (!isAutoModeEnabled(context)) return
        scheduleNextDhuhrOrJumuahAlarm(context)
        scheduleNextMaghribAlarm(context)
        if (hasRecordAudioPermission(context) &&
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            AzanListenerService.start(context)
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

    fun computeNextMaghribTarget(
        district: District,
        now: Calendar = Calendar.getInstance()
    ): MaghribAutoTarget {
        val todaySchedule = PrayerTimeCalculator.calculate(now, district)
        val todayMaghrib = (todaySchedule.maghribCal.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetCal = if (todayMaghrib.timeInMillis > now.timeInMillis) {
            todayMaghrib
        } else {
            val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val tomorrowSchedule = PrayerTimeCalculator.calculate(tomorrow, district)
            (tomorrowSchedule.maghribCal.clone() as Calendar).apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        return MaghribAutoTarget(
            triggerAtMillis = targetCal.timeInMillis,
            label = "মাগরিব সালাত (অটো)",
            formattedTime = timeFmt.format(targetCal.timeInMillis)
        )
    }

    fun computeNextMaghribTarget(
        context: Context,
        now: Calendar = Calendar.getInstance()
    ): MaghribAutoTarget {
        val district = loadSavedDistrict(context)
        return computeNextMaghribTarget(district, now)
    }

    fun scheduleNextDhuhrOrJumuahAlarm(context: Context, now: Calendar = Calendar.getInstance()): Long {
        val target = computeNextDhuhrOrJumuahTarget(now)
        scheduleExactAutoAlarm(
            context = context,
            action = ACTION_AUTO_DHUHR_ALARM,
            requestCode = AUTO_DHUHR_REQ_CODE,
            triggerAtMillis = target.triggerAtMillis,
            label = target.label
        )
        return target.triggerAtMillis
    }

    fun scheduleNextMaghribAlarm(context: Context, now: Calendar = Calendar.getInstance()): Long {
        val target = computeNextMaghribTarget(context, now)
        scheduleExactAutoAlarm(
            context = context,
            action = ACTION_AUTO_MAGHRIB_ALARM,
            requestCode = AUTO_MAGHRIB_REQ_CODE,
            triggerAtMillis = target.triggerAtMillis,
            label = target.label
        )
        return target.triggerAtMillis
    }

    private fun scheduleExactAutoAlarm(
        context: Context,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long,
        label: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_AUTO_LABEL, label)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 1,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } catch (e: SecurityException) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (_: Exception) {
            }
        }
    }

    fun cancelDhuhrOrJumuahAlarm(context: Context) {
        cancelAutoAlarm(context, ACTION_AUTO_DHUHR_ALARM, AUTO_DHUHR_REQ_CODE)
    }

    fun cancelMaghribAlarm(context: Context) {
        cancelAutoAlarm(context, ACTION_AUTO_MAGHRIB_ALARM, AUTO_MAGHRIB_REQ_CODE)
    }

    private fun cancelAutoAlarm(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
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
        val district = loadSavedDistrict(context)
        val now = Calendar.getInstance()
        val nextMaghrib = computeNextMaghribTarget(district, now)
        if (!isAutoModeEnabled(context)) {
            return "বন্ধ আছে • চালু করলে প্রতিদিন যোহর (১:২০), জুমা (১২:৪৫) ও মাগরিব (${nextMaghrib.formattedTime}) ২০ মি. এবং আসর ও ইশার আজান শুনলেই ১৫ মি. এলার্ম বাজবে।"
        }
        val next = computeNextDhuhrOrJumuahTarget(now)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val nextDayText = if (next.isFriday) "জুমা" else "যোহর"
        val micPart = if (hasRecordAudioPermission(context)) {
            " • আসর/ইশা আজান শনাক্ত (১৫ মি.) সক্রিয়"
        } else {
            " • আসর/ইশার জন্য মাইক্রোফোন অনুমতি দিন"
        }
        return "✅ চালু আছে • পরবর্তী $nextDayText: ${timeFmt.format(next.triggerAtMillis)} ও মাগরিব: ${nextMaghrib.formattedTime} (২০ মি.)$micPart"
    }
}
