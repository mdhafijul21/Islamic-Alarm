package com.hafij.islamicalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.auto.AutoAlarmManager
import com.hafij.islamicalarm.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AutoAlarmManager.ACTION_AUTO_DHUHR_ALARM) {
            if (!AutoAlarmManager.isAutoModeEnabled(context)) return
            val label = intent.getStringExtra(AutoAlarmManager.EXTRA_AUTO_LABEL)
                ?.takeIf { it.isNotBlank() }
                ?: "যোহর/জুমা সালাত (অটো)"
            triggerLockSession(
                context = context,
                alarmId = "auto_dhuhr_jumuah_${System.currentTimeMillis() / 60000L}",
                lockDurationMinutes = AutoAlarmManager.AUTO_DURATION_MINUTES,
                label = label,
                fromAlarm = true
            )
            // Schedule tomorrow's 1:20 PM (or Friday 12:45 PM)
            AutoAlarmManager.scheduleNextDhuhrOrJumuahAlarm(context)
            return
        }

        if (intent.action == AutoAlarmManager.ACTION_AUTO_MAGHRIB_ALARM) {
            if (!AutoAlarmManager.isAutoModeEnabled(context)) return
            val label = intent.getStringExtra(AutoAlarmManager.EXTRA_AUTO_LABEL)
                ?.takeIf { it.isNotBlank() }
                ?: "মাগরিব সালাত (অটো)"
            triggerLockSession(
                context = context,
                alarmId = "auto_maghrib_${System.currentTimeMillis() / 60000L}",
                lockDurationMinutes = AutoAlarmManager.AUTO_DURATION_MINUTES,
                label = label,
                fromAlarm = true
            )
            // Schedule tomorrow's Maghrib based on saved district
            AutoAlarmManager.scheduleNextMaghribAlarm(context)
            return
        }

        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val alarmStore = AlarmStore(context)
        val alarm = alarmStore.getAlarm(alarmId)

        if (alarm == null || !alarm.isEnabled) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(alarmId.hashCode())
            return
        }

        if (alarm.isEnabled) {
            triggerLockSession(
                context = context,
                alarmId = alarm.id,
                lockDurationMinutes = alarm.lockDurationMinutes,
                label = alarm.label,
                fromAlarm = true
            )

            // Reschedule or disable based on repeat mode
            if (alarm.isRepeatDaily) {
                AlarmScheduler.scheduleAlarm(context, alarm)
            } else {
                val updated = alarm.copy(isEnabled = false)
                alarmStore.updateAlarm(updated)
            }
        }
    }

    companion object {
        fun triggerLockSession(
            context: Context,
            alarmId: String,
            lockDurationMinutes: Int,
            label: String,
            fromAlarm: Boolean
        ) {
            // Enable Auto Silent mode for prayer
            com.hafij.islamicalarm.silent.AutoSilentManager.enableSilentMode(context)

            // Wake CPU immediately
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "IslamicAlarm:ReceiverWakeLock"
            )
            wakeLock.acquire(10 * 1000L)

            // Start the Foreground Service that displays the full-screen overlay layout
            var serviceStarted = false
            try {
                val serviceIntent = PrayerLockOverlayService.createStartIntent(
                    context = context,
                    alarmId = alarmId,
                    lockDurationMinutes = lockDurationMinutes,
                    label = label,
                    fromAlarm = fromAlarm
                )
                ContextCompat.startForegroundService(context, serviceIntent)
                serviceStarted = true
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback to LockScreenActivity + high-priority FullScreenIntent when overlay permission
            // is not granted or foreground service start is blocked.
            if (!serviceStarted || !Settings.canDrawOverlays(context)) {
                val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                    putExtra("ALARM_ID", alarmId)
                    putExtra("LOCK_DURATION_MINUTES", lockDurationMinutes)
                    putExtra("LABEL", label)
                    putExtra("FROM_ALARM", fromAlarm)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }

                try {
                    context.startActivity(lockIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (!serviceStarted) {
                    postFallbackAlarmNotification(context, alarmId, label, lockIntent)
                }
            }
        }

        private fun postFallbackAlarmNotification(
        context: Context,
        alarmId: String,
        label: String,
        lockIntent: Intent
    ) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode() + 2000,
            lockIntent,
            flags
        )

        val channelId = "islamic_alarm_channel_v5_silent"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Islamic Alarm Lock (Silent Ringtone)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prayer Time Lock Screen Alarm"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationTitle = if (label.isNotBlank()) {
            "$label - নামাজের সময় হয়েছে"
        } else {
            "নামাজের সময় হয়েছে"
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText("ফোন লক হচ্ছে, নামাজের প্রস্তুতি নিন")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notificationManager.notify(alarmId.hashCode(), notificationBuilder.build())
        }
    }
}
