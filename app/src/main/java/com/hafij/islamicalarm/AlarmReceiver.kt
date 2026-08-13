package com.hafij.islamicalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hafij.islamicalarm.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val alarmStore = AlarmStore(context)
        val alarm = alarmStore.getAlarm(alarmId) ?: return

        if (alarm.isEnabled) {
            // 1. Launch LockScreenActivity
            val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                putExtra("LOCK_DURATION_MINUTES", alarm.lockDurationMinutes)
                putExtra("LABEL", alarm.label)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(lockIntent)

            // 2. Reschedule or disable based on repeat mode
            if (alarm.isRepeatDaily) {
                AlarmScheduler.scheduleAlarm(context, alarm)
            } else {
                val updated = alarm.copy(isEnabled = false)
                alarmStore.updateAlarm(updated)
            }
        }
    }
}
