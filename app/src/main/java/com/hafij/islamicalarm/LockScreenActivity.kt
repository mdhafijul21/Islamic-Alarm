package com.hafij.islamicalarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hafij.islamicalarm.audio.AzanData
import com.hafij.islamicalarm.data.AlarmStore
import com.hafij.islamicalarm.databinding.ActivityLockScreenBinding
import java.util.Locale

class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private var countDownTimer: CountDownTimer? = null
    private var remainingTimeMillis: Long = 0L
    private var isCallInProgress: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var textToSpeech: TextToSpeech? = null
    private var ringtone: Ringtone? = null
    private var azanMediaPlayer: android.media.MediaPlayer? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

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
            val currentAlarmId = getSharedPreferences("IslamicAlarmLockPrefs", Context.MODE_PRIVATE)
                .getString("active_alarm_id", null)
            if (dismissedId == null || dismissedId == currentAlarmId) {
                releaseLockAndFinish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configure Window Flags to display on top of Lock Screen and keep screen ON
        setupLockScreenFlags()

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Hide Status Bar and Navigation Bar (Immersive Sticky Mode)
        hideSystemUI()

        // Enable Screen Pinning / Lock Task mode if supported/available
        try {
            startLockTask()
        } catch (e: Exception) {
            // Non-kiosk or standard mode fallback
        }

        // 3. Disable Back Button Completely
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Completely disabled - do nothing
            }
        })

        // Emergency / Prayer Complete Unlock with 3s long press
        binding.btnUnlockScreen.setOnClickListener {
            android.widget.Toast.makeText(
                this,
                "নামাজ সম্পন্ন হলে বা জরুরি প্রয়োজনে ৩ সেকেন্ড চেপে ধরে রাখুন।",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnUnlockScreen.setOnLongClickListener {
            android.widget.Toast.makeText(this, "নামাজ সম্পন্ন হয়েছে। লক স্ক্রিন সমাপ্ত করা হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
            releaseLockAndFinish()
            true
        }

        // 4. Initialize or Resume Timer and Verify Alarm Validity
        initOrResumeLockTimer(intent)

        // 5. Acquire WakeLock
        acquireWakeLock()

        // 7. Register Phone Call State & Dismiss Receivers
        val callFilter = IntentFilter(CallReceiver.ACTION_CALL_STATE_CHANGED)
        ContextCompat.registerReceiver(
            this,
            callStateReceiver,
            callFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val dismissFilter = IntentFilter(AlarmScheduler.ACTION_DISMISS_ALARM)
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            dismissFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initOrResumeLockTimer(intent)
    }

    override fun onResume() {
        super.onResume()
        initOrResumeLockTimer(intent)
    }

    private fun initOrResumeLockTimer(intent: Intent) {
        val prefs = getSharedPreferences("IslamicAlarmLockPrefs", Context.MODE_PRIVATE)
        val savedAlarmId = prefs.getString("active_alarm_id", null)
        val intentAlarmId = intent.getStringExtra("ALARM_ID")
        val currentAlarmId = intentAlarmId ?: savedAlarmId ?: ""

        val alarmStore = AlarmStore(this)
        if (currentAlarmId.isNotBlank()) {
            val alarm = alarmStore.getAlarm(currentAlarmId)
            // If alarm was deleted or toggled off, finish lock activity immediately
            if (alarm == null || !alarm.isEnabled) {
                releaseLockAndFinish()
                return
            }
        }

        val now = System.currentTimeMillis()
        var endTime = prefs.getLong("active_end_time", 0L)
        val label = intent.getStringExtra("LABEL")
            ?: prefs.getString("active_label", null)
            ?: ""

        if (label.isNotBlank()) {
            binding.tvLockAlarmLabel.text = label
            binding.tvLockAlarmLabel.visibility = View.VISIBLE
        } else {
            binding.tvLockAlarmLabel.visibility = View.GONE
        }

        if (endTime > now && savedAlarmId == currentAlarmId) {
            // Continuation of active timer
            remainingTimeMillis = endTime - now
        } else {
            // New lock screen session
            val durationMinutes = intent.getIntExtra("LOCK_DURATION_MINUTES", 15)
            remainingTimeMillis = durationMinutes * 60 * 1000L
            endTime = now + remainingTimeMillis

            prefs.edit()
                .putString("active_alarm_id", currentAlarmId)
                .putLong("active_end_time", endTime)
                .putString("active_label", label)
                .apply()

            // Play audio/TTS only on fresh start
            if (ringtone == null) {
                playAlarmSound()
            }
            if (textToSpeech == null) {
                initTextToSpeech(label)
            }
        }

        if (remainingTimeMillis <= 0) {
            releaseLockAndFinish()
            return
        }

        startCountdown(remainingTimeMillis)
    }

    private fun playAlarmSound() {
        val prefs = getSharedPreferences("IslamicAlarmPrefs", Context.MODE_PRIVATE)
        val isAzanEnabled = prefs.getBoolean(AzanData.PREF_AZAN_SOUND_ENABLED, true)
        val selectedAzanId = prefs.getString(AzanData.PREF_SELECTED_AZAN_ID, "makkah_ali_mullah") ?: "makkah_ali_mullah"
        val azanItem = AzanData.azanList.find { it.id == selectedAzanId } ?: AzanData.azanList.first()

        if (isAzanEnabled && azanItem.audioUrl.isNotBlank()) {
            try {
                azanMediaPlayer = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setDataSource(azanItem.audioUrl)
                    isLooping = true
                    setOnPreparedListener { mp ->
                        mp.start()
                    }
                    setOnErrorListener { _, _, _ ->
                        playDefaultSystemRingtone()
                        true
                    }
                    prepareAsync()
                }
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        playDefaultSystemRingtone()
    }

    private fun playDefaultSystemRingtone() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
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

        try {
            ringtone?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ringtone = null
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

                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (remainingTimeMillis > 0 && !isFinishing) {
                            handler.postDelayed({
                                try {
                                    textToSpeech?.speak(speechMsg, TextToSpeech.QUEUE_FLUSH, null, "IslamicAlarmTTS_Loop")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, 1500)
                        }
                    }
                    override fun onError(utteranceId: String?) {}
                })

                textToSpeech?.speak(speechMsg, TextToSpeech.QUEUE_FLUSH, null, "IslamicAlarmTTS_Loop")
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (remainingTimeMillis > 0) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_HOME,
                android.view.KeyEvent.KEYCODE_APP_SWITCH,
                android.view.KeyEvent.KEYCODE_MENU,
                android.view.KeyEvent.KEYCODE_SEARCH -> {
                    // Consume key event completely
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (remainingTimeMillis > 0) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_HOME,
                android.view.KeyEvent.KEYCODE_APP_SWITCH,
                android.view.KeyEvent.KEYCODE_MENU,
                android.view.KeyEvent.KEYCODE_SEARCH -> {
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (remainingTimeMillis > 0) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_HOME,
                android.view.KeyEvent.KEYCODE_APP_SWITCH,
                android.view.KeyEvent.KEYCODE_MENU,
                android.view.KeyEvent.KEYCODE_SEARCH -> {
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        if (remainingTimeMillis > 0 && !isCallInProgress && !isFinishing) {
            relaunchLockScreen()
        }
    }

    override fun onStop() {
        super.onStop()
        if (remainingTimeMillis > 0 && !isCallInProgress && !isFinishing) {
            relaunchLockScreen()
        }
    }

    private fun startCountdown(millis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMillis = millisUntilFinished
                updateTimerDisplay(millisUntilFinished)
            }

            override fun onFinish() {
                binding.tvCountdownTimer.text = "00:00"
                releaseLockAndFinish()
            }
        }.start()
    }

    private fun updateTimerDisplay(millis: Long) {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val timeString = String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds)
        binding.tvCountdownTimer.text = timeString
    }

    private fun handleCallState(state: String?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                isCallInProgress = true
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isCallInProgress) {
                    isCallInProgress = false
                    if (remainingTimeMillis > 0) {
                        hideSystemUI()
                    }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "IslamicAlarm:LockScreenWakeLock"
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

    private fun releaseLockAndFinish() {
        remainingTimeMillis = 0L
        val prefs = getSharedPreferences("IslamicAlarmLockPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        try {
            stopLockTask()
        } catch (e: Exception) {
            // Safe fallback
        }

        com.hafij.islamicalarm.silent.AutoSilentManager.restoreRingerMode(this)
        stopAlarmSound()
        releaseWakeLock()
        finish()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (remainingTimeMillis > 0 && !isCallInProgress && !isFinishing) {
            relaunchLockScreen()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && remainingTimeMillis > 0 && !isCallInProgress && !isFinishing) {
            hideSystemUI()
            try {
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed({
                if (remainingTimeMillis > 0 && !isCallInProgress && !isFinishing) {
                    relaunchLockScreen()
                }
            }, 200)
        }
    }

    private fun relaunchLockScreen() {
        try {
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.hafij.islamicalarm.silent.AutoSilentManager.restoreRingerMode(this)
        stopAlarmSound()
        countDownTimer?.cancel()
        releaseWakeLock()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
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
    }
}
