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

    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CallReceiver.ACTION_CALL_STATE_CHANGED) {
                val state = intent.getStringExtra(CallReceiver.EXTRA_CALL_STATE)
                handleCallState(state)
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

        // 3. Disable Back Button Completely
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to prevent escaping the lock screen
            }
        })

        // 4. Retrieve Lock Duration and Label
        val durationMinutes = intent.getIntExtra("LOCK_DURATION_MINUTES", 15)
        val label = intent.getStringExtra("LABEL") ?: ""

        if (label.isNotBlank()) {
            binding.tvLockAlarmLabel.text = label
            binding.tvLockAlarmLabel.visibility = View.VISIBLE
        } else {
            binding.tvLockAlarmLabel.visibility = View.GONE
        }

        remainingTimeMillis = durationMinutes * 60 * 1000L

        // 5. Start Screen Pinning / Lock Task
        enableScreenPinning()

        // 6. Start Live Countdown Timer
        startCountdown(remainingTimeMillis)

        // 7. Acquire WakeLock to keep screen awake during alarm lock
        acquireWakeLock()

        // 8. Register Phone Call State Receiver
        val filter = IntentFilter(CallReceiver.ACTION_CALL_STATE_CHANGED)
        ContextCompat.registerReceiver(
            this,
            callStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 9. Play Loud Alarm Ringtone and Bengali Voice Announcement
        playAlarmSound()
        initTextToSpeech(label)
    }

    private fun playAlarmSound() {
        try {
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
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
            ringtone?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ringtone = null
    }

    private fun initTextToSpeech(label: String) {
        textToSpeech = TextToSpeech(this) { status ->
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

                // Announce voice message 3 times clearly
                for (i in 1..3) {
                    textToSpeech?.speak(speechMsg, TextToSpeech.QUEUE_ADD, null, "IslamicAlarmTTS_$i")
                }
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

    private fun enableScreenPinning() {
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun disableScreenPinning() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
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
                disableScreenPinning()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (isCallInProgress) {
                    isCallInProgress = false
                    if (remainingTimeMillis > 0) {
                        enableScreenPinning()
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
            acquire(30 * 60 * 1000L) // Safe limit
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
        stopAlarmSound()
        disableScreenPinning()
        releaseWakeLock()
        finish()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (remainingTimeMillis > 0 && !isCallInProgress && !isFinishing) {
            relaunchLockScreen()
        }
    }

    override fun onPause() {
        super.onPause()
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
    }
}
