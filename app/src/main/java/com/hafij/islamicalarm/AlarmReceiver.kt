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
import com.hafij.islamicalarm.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
                    alarmId = alarm.id,
                    lockDurationMinutes = alarm.lockDurationMinutes,
                    label = alarm.label,
                    fromAlarm = true
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
                    putExtra("ALARM_ID", alarm.id)
                    putExtra("LOCK_DURATION_MINUTES", alarm.lockDurationMinutes)
                    putExtra("LABEL", alarm.label)
                    putExtra("FROM_ALARM", true)
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
                    postFallbackAlarmNotification(context, alarm.id, alarm.label, lockIntent)
                }
            }

            // Reschedule or disable based on repeat mode
            if (alarm.isRepeatDaily) {
                AlarmScheduler.scheduleAlarm(context, alarm)
            } else {
                val updated = alarm.copy(isEnabled = false)
                alarmStore.updateAlarm(updated)
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

        val channelId = "islamic_alarm_channel_v4"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Islamic Alarm Clock",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prayer Time Lock Screen Alarm"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(alarmSoundUri, audioAttributes)
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
            .setSound(alarmSoundUri)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notificationManager.notify(alarmId.hashCode(), notificationBuilder.build())
    }
}
