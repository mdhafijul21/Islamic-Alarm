package com.hafij.islamicalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "IslamicAlarm:ReceiverWakeLock"
            )
            wakeLock.acquire(10 * 1000L)

            // Intent for LockScreenActivity
            val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                putExtra("LOCK_DURATION_MINUTES", alarm.lockDurationMinutes)
                putExtra("LABEL", alarm.label)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            // 1. Directly trigger startActivity first to popup full screen on top of home screen/other apps
            try {
                context.startActivity(lockIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                alarm.id.hashCode() + 2000,
                lockIntent,
                flags
            )

            // Notification Channel for High Priority Alarm
            val channelId = "islamic_alarm_channel"
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Islamic Alarm Clock",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Prayer Time Lock Screen Alarm"
                    enableVibration(true)
                    setBypassDnd(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notificationTitle = if (alarm.label.isNotBlank()) {
                "${alarm.label} - নামাজের সময় হয়েছে"
            } else {
                "নামাজের সময় হয়েছে"
            }

            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(notificationTitle)
                .setContentText("ফোন লক হচ্ছে, নামাজের প্রস্তুতি নিন")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            notificationManager.notify(alarm.id.hashCode(), notificationBuilder.build())

            // Also directly trigger startActivity
            try {
                context.startActivity(lockIntent)
            } catch (e: Exception) {
                e.printStackTrace()
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
}
