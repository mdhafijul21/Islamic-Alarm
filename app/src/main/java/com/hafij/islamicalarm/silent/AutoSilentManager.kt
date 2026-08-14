package com.hafij.islamicalarm.silent

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build

object AutoSilentManager {

    private const val PREFS_NAME = "IslamicAutoSilentPrefs"
    private const val KEY_AUTO_SILENT_ENABLED = "auto_silent_enabled"
    private const val KEY_PREVIOUS_RINGER_MODE = "previous_ringer_mode"

    fun isAutoSilentEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SILENT_ENABLED, true) // Default enabled
    }

    fun setAutoSilentEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_SILENT_ENABLED, enabled).apply()
    }

    fun hasDndPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    fun requestDndPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun enableSilentMode(context: Context) {
        if (!isAutoSilentEnabled(context)) return

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Save current ringer mode
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_PREVIOUS_RINGER_MODE, audioManager.ringerMode).apply()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                } else {
                    // Fallback to Vibrate if full Silent policy permission is restricted
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreRingerMode(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val previousMode = prefs.getInt(KEY_PREVIOUS_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    audioManager.ringerMode = previousMode
                } else {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            } else {
                audioManager.ringerMode = previousMode
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
