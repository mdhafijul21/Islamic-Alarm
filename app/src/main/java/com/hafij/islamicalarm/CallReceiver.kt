package com.hafij.islamicalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val callStateIntent = Intent(ACTION_CALL_STATE_CHANGED).apply {
                putExtra(EXTRA_CALL_STATE, stateStr)
                setPackage(context.packageName)
            }
            context.sendBroadcast(callStateIntent)
        }
    }

    companion object {
        const val ACTION_CALL_STATE_CHANGED = "com.hafij.islamicalarm.CALL_STATE_CHANGED"
        const val EXTRA_CALL_STATE = "extra_call_state"
    }
}
