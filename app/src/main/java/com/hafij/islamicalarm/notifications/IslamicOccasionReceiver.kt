package com.hafij.islamicalarm.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IslamicOccasionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == IslamicOccasionNotifier.ACTION_CHECK_DAILY_OCCASION ||
            intent.action == Intent.ACTION_DATE_CHANGED
        ) {
            val appCtx = context.applicationContext
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    IslamicOccasionNotifier.evaluateAndNotifyToday(appCtx)
                    IslamicOccasionNotifier.evaluateAndNotifyDailyHadith(appCtx, allowNetwork = true)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
