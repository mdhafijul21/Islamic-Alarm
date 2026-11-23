package com.hafij.islamicalarm.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class IslamicOccasionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == IslamicOccasionNotifier.ACTION_CHECK_DAILY_OCCASION ||
            intent.action == Intent.ACTION_DATE_CHANGED
        ) {
            IslamicOccasionNotifier.evaluateAndNotifyToday(context)
        }
    }
}
