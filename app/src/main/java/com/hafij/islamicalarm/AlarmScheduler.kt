package com.hafij.islamicalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hafij.islamicalarm.data.AlarmItem
import com.hafij.islamicalarm.data.AlarmStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val ACTION_DISMISS_ALARM = "com.hafij.islamicalarm.ACTION_DISMISS_ALARM"

    fun scheduleAlarm(context: Context, alarm: AlarmItem): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If time is equal or in the past, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val triggerAtMillis = calendar.timeInMillis

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            flags
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode() + 1000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            e.printStackTrace()
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        return triggerAtMillis
    }

    fun cancelAlarm(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            flags
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        try {
            val dismissIntent = Intent(ACTION_DISMISS_ALARM).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
            context.sendBroadcast(dismissIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun rescheduleAllAlarms(context: Context) {
        val alarmStore = AlarmStore(context)
        val alarms = alarmStore.getAlarms()
        for (alarm in alarms) {
            if (alarm.isEnabled) {
                scheduleAlarm(context, alarm)
            }
        }
    }

    fun getFormattedNextRingTime(context: Context, triggerMillis: Long): String {
        val nowCal = Calendar.getInstance()
        val triggerCal = Calendar.getInstance().apply { timeInMillis = triggerMillis }

        val isToday = nowCal.get(Calendar.DAY_OF_YEAR) == triggerCal.get(Calendar.DAY_OF_YEAR) &&
                nowCal.get(Calendar.YEAR) == triggerCal.get(Calendar.YEAR)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val formattedTime = timeFormat.format(triggerCal.time)

        return if (isToday) {
            context.getString(R.string.alarm_set_today, formattedTime)
        } else {
            context.getString(R.string.alarm_set_tomorrow, formattedTime)
        }
    }
}
