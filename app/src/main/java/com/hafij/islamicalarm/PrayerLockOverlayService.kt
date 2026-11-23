package com.hafij.islamicalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hafij.islamicalarm.audio.AzanData
import com.hafij.islamicalarm.databinding.ActivityLockScreenBinding
import com.hafij.islamicalarm.silent.AutoSilentManager
import java.util.Locale

/**
 * Foreground service triggered at pre-configured prayer times to display a
 * full-screen WindowManager overlay restricting phone usage for the lock duration.
 * Falls back to LockScreenActivity + FullScreenIntent if overlay permission is not granted.
 */
class PrayerLockOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayBinding: ActivityLockScreenBinding? = null
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var lockEndElapsedMs: Long = 0L
    private var lastDisplayedSeconds: Long = -1L
    private var isTickerRunning: Boolean = false
    private var remainingTimeMillis: Long = 0L
    private var isCallInProgress: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var textToSpeech: TextToSpeech? = null
    private var azanMediaPlayer: MediaPlayer? = null
    private var receiversRegistered = false
    private var audioStarted = false

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!isTickerRunning) return
            val rem = (lockEndElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingTimeMillis = rem
            val displaySeconds = if (rem <= 0L) 0L else (rem + 999L) / 1000L
            if (displaySeconds != lastDisplayedSeconds) {
                lastDisplayedSeconds = displaySeconds
                updateTimerSecondsDisplay(displaySeconds)
            }
            if (rem <= 0L) {
                isTickerRunning = false
                releaseLockAndStop()
            } else {
                countdownHandler.postDelayed(this, 200L)
            }
        }
    }

    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CallReceiver.ACTION_CALL_STATE_CHANGED) {
                val state = intent.getStringExtra(CallReceiver.EXTRA_CALL_STATE)
                handleCallState(state)
            }
        }
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val dismissedId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
            val currentAlarmId = getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_ALARM_ID, null)
            if (dismissedId == null || dismissedId == currentAlarmId) {
                releaseLockAndStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        registerReceiversIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID)
        val durationMinutes = intent?.getIntExtra(EXTRA_LOCK_DURATION_MINUTES, 15)?.coerceAtLeast(1) ?: 15
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        val fromAlarm = intent?.getBooleanExtra(EXTRA_FROM_ALARM, false) ?: false

        val notification = buildForegroundNotification(alarmId, durationMinutes, label, fromAlarm)
        startForegroundCompat(notification)

        val now = System.currentTimeMillis()
        if (fromAlarm && alarmId != null && wasRecentlyFinished(alarmId, now)) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE)
        val savedAlarmId = prefs.getString(KEY_ACTIVE_ALARM_ID, null)
        val savedEnd = prefs.getLong(KEY_ACTIVE_END_TIME, 0L)
        val hasActiveSession = !savedAlarmId.isNullOrEmpty() && savedEnd > now

        val activeLabel = label.ifBlank { prefs.getString(KEY_ACTIVE_LABEL, "").orEmpty() }

        remainingTimeMillis = when {
            hasActiveSession && (alarmId == null || alarmId == savedAlarmId) -> savedEnd - now
            alarmId != null -> {
                val durationMs = durationMinutes * 60_000L
                prefs.edit()
                    .putString(KEY_ACTIVE_ALARM_ID, alarmId)
                    .putLong(KEY_ACTIVE_END_TIME, now + durationMs)
                    .putString(KEY_ACTIVE_LABEL, activeLabel)
                    .apply()
                durationMs
            }
            else -> 0L
        }

        if (remainingTimeMillis <= 0L) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        AutoSilentManager.enableSilentMode(this)
        acquireWakeLock()

        val canOverlay = Settings.canDrawOverlays(this)
        if (canOverlay && showFullScreenOverlay(activeLabel)) {
            isOverlayShowing = true
            startCountdown(remainingTimeMillis)
            if (!audioStarted) {
                audioStarted = true
                playAlarmSound()
                initTextToSpeech(activeLabel)
            }
        } else {
            // Overlay permission missing or window failed: fall back to LockScreenActivity
            isOverlayShowing = false
            launchLockScreenFallback(alarmId, durationMinutes, activeLabel, fromAlarm)
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(
        alarmId: String?,
        durationMinutes: Int,
        label: String,
        fromAlarm: Boolean
    ): Notification {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
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

        val lockIntent = createLockActivityIntent(alarmId, durationMinutes, label, fromAlarm)
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            (alarmId?.hashCode() ?: 0) + 2000,
            lockIntent,
            pendingFlags
        )

        val notificationTitle = if (label.isNotBlank()) {
            "$label - নামাজের সময় হয়েছে"
        } else {
            "নামাজের সময় হয়েছে"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText("ফোন লক সক্রিয়, নামাজের প্রস্তুতি নিন")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun showFullScreenOverlay(label: String): Boolean {
        if (overlayBinding != null) {
            updateOverlayLabel(label)
            return true
        }

        return try {
            val themedContext = ContextThemeWrapper(this, R.style.Theme_IslamicAlarm_LockScreen)
            val binding = ActivityLockScreenBinding.inflate(LayoutInflater.from(themedContext))

            @Suppress("DEPRECATION")
            val layoutFlags = (
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                layoutFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            binding.root.isFocusableInTouchMode = true
            binding.root.requestFocus()
            binding.root.setOnKeyListener { _, keyCode, _ ->
                if (remainingTimeMillis > 0L) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_HOME,
                        KeyEvent.KEYCODE_APP_SWITCH,
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_SEARCH -> true
                        else -> false
                    }
                } else {
                    false
                }
            }

            overlayBinding = binding
            updateOverlayLabel(label)
            updateTimerDisplay(remainingTimeMillis)

            windowManager?.addView(binding.root, params)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            overlayBinding = null
            false
        }
    }

    private fun updateOverlayLabel(label: String) {
        val binding = overlayBinding ?: return
        if (label.isNotBlank()) {
            binding.tvLockAlarmLabel.text = label
            binding.tvLockAlarmLabel.visibility = View.VISIBLE
        } else {
            binding.tvLockAlarmLabel.visibility = View.GONE
        }
    }

    private fun removeOverlay() {
        val view = overlayBinding?.root
        if (view != null && view.isAttachedToWindow) {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (e: Exception) {
                try {
                    windowManager?.removeView(view)
                } catch (ignored: Exception) {
                }
            }
        }
        overlayBinding = null
        isOverlayShowing = false
    }

    private fun startCountdown(millis: Long) {
        val targetEndElapsed = SystemClock.elapsedRealtime() + millis
        if (isTickerRunning && kotlin.math.abs(lockEndElapsedMs - targetEndElapsed) < 1500L) {
            return
        }
        countdownHandler.removeCallbacks(countdownRunnable)
        lockEndElapsedMs = targetEndElapsed
        lastDisplayedSeconds = -1L
        isTickerRunning = true
        countdownHandler.post(countdownRunnable)
    }

    private fun stopCountdown() {
        isTickerRunning = false
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    private fun updateTimerDisplay(millis: Long) {
        val totalSeconds = if (millis <= 0L) 0L else (millis + 999L) / 1000L
        updateTimerSecondsDisplay(totalSeconds)
    }

    private fun updateTimerSecondsDisplay(totalSeconds: Long) {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val minutes = clamped / 60L
        val seconds = clamped % 60L
        overlayBinding?.tvCountdownTimer?.text =
            String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
    }

    private fun handleCallState(state: String?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                isCallInProgress = true
                overlayBinding?.root?.visibility = View.GONE
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isCallInProgress) {
                    isCallInProgress = false
                    if (remainingTimeMillis > 0L) {
                        overlayBinding?.root?.visibility = View.VISIBLE
                        overlayBinding?.root?.requestFocus()
                    }
                }
            }
        }
    }

    private fun launchLockScreenFallback(
        alarmId: String?,
        durationMinutes: Int,
        label: String,
        fromAlarm: Boolean
    ) {
        try {
            startActivity(createLockActivityIntent(alarmId, durationMinutes, label, fromAlarm))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createLockActivityIntent(
        alarmId: String?,
        durationMinutes: Int,
        label: String,
        fromAlarm: Boolean
    ): Intent {
        return Intent(this, LockScreenActivity::class.java).apply {
            if (alarmId != null) putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_LOCK_DURATION_MINUTES, durationMinutes)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_FROM_ALARM, fromAlarm)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
    }

    private fun playAlarmSound() {
        val prefs = getSharedPreferences("IslamicAlarmPrefs", Context.MODE_PRIVATE)
        val isAzanEnabled = prefs.getBoolean(AzanData.PREF_AZAN_SOUND_ENABLED, true)
        val selectedAzanId =
            prefs.getString(AzanData.PREF_SELECTED_AZAN_ID, "makkah_ali_mullah") ?: "makkah_ali_mullah"
        val azanItem = AzanData.azanList.find { it.id == selectedAzanId } ?: AzanData.azanList.first()

        if (isAzanEnabled && azanItem.audioUrl.isNotBlank()) {
            try {
                azanMediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setDataSource(azanItem.audioUrl)
                    isLooping = true
                    setOnPreparedListener { mp -> mp.start() }
                    setOnErrorListener { _, _, _ ->
                        // Never play a system ringtone as fallback when alarm rings
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopAlarmSound() {
        try {
            azanMediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        azanMediaPlayer = null
    }

    private fun initTextToSpeech(label: String) {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val bnLocale = Locale("bn", "BD")
                val result = textToSpeech?.setLanguage(bnLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.language = Locale("bn")
                }

                val speechMsg = if (label.isNotBlank()) {
                    "$label এর নামাজের সময় হয়েছে, নামাজে যান।"
                } else {
                    "নামাজের সময় হয়েছে, নামাজে যান।"
                }
                textToSpeech?.speak(speechMsg, TextToSpeech.QUEUE_FLUSH, null, "IslamicAlarmOverlayTTS")
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "IslamicAlarm:OverlayServiceWakeLock"
        ).apply {
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun wasRecentlyFinished(alarmId: String, now: Long): Boolean {
        val history = getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
        return history.getString(KEY_LAST_ID, null) == alarmId &&
            now - history.getLong(KEY_LAST_FINISH, 0L) < 120_000L
    }

    private fun releaseLockAndStop() {
        remainingTimeMillis = 0L
        val prefs = getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE)
        val endedId = prefs.getString(KEY_ACTIVE_ALARM_ID, null)
        if (!endedId.isNullOrEmpty()) {
            getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_ID, endedId)
                .putLong(KEY_LAST_FINISH, System.currentTimeMillis())
                .apply()
        }
        prefs.edit().clear().apply()

        AutoSilentManager.restoreRingerMode(this)
        stopAlarmSound()
        stopCountdown()
        removeOverlay()
        releaseWakeLock()
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun registerReceiversIfNeeded() {
        if (receiversRegistered) return
        ContextCompat.registerReceiver(
            this,
            callStateReceiver,
            IntentFilter(CallReceiver.ACTION_CALL_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(AlarmScheduler.ACTION_DISMISS_ALARM),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiversRegistered = true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
        stopAlarmSound()
        removeOverlay()
        releaseWakeLock()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        if (receiversRegistered) {
            try {
                unregisterReceiver(callStateReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                unregisterReceiver(dismissReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            receiversRegistered = false
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_LOCK_DURATION_MINUTES = "LOCK_DURATION_MINUTES"
        const val EXTRA_LABEL = "LABEL"
        const val EXTRA_FROM_ALARM = "FROM_ALARM"

        private const val CHANNEL_ID = "islamic_alarm_channel_v5_silent"
        private const val NOTIFICATION_ID = 4109
        private const val PREFS_LOCK = "IslamicAlarmLockPrefs"
        private const val PREFS_HISTORY = "IslamicAlarmLockHistory"
        private const val KEY_ACTIVE_ALARM_ID = "active_alarm_id"
        private const val KEY_ACTIVE_END_TIME = "active_end_time"
        private const val KEY_ACTIVE_LABEL = "active_label"
        private const val KEY_LAST_ID = "last_id"
        private const val KEY_LAST_FINISH = "last_finish"

        @Volatile
        var isOverlayShowing: Boolean = false
            private set

        fun createStartIntent(
            context: Context,
            alarmId: String,
            lockDurationMinutes: Int,
            label: String,
            fromAlarm: Boolean
        ): Intent {
            return Intent(context, PrayerLockOverlayService::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_LOCK_DURATION_MINUTES, lockDurationMinutes)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_FROM_ALARM, fromAlarm)
            }
        }
    }
}
