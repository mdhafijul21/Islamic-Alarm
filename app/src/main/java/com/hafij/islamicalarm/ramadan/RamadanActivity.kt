package com.hafij.islamicalarm.ramadan

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.databinding.ActivityRamadanBinding
import java.util.Calendar
import java.util.Locale

class RamadanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRamadanBinding
    private var keptFasts = 0
    private var missedFasts = 0
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRamadanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarRamadan.setNavigationOnClickListener {
            finish()
        }

        loadSavedCounts()
        updateCounterUi()

        binding.btnIncFasts.setOnClickListener {
            keptFasts++
            saveCounts()
            updateCounterUi()
        }

        binding.btnDecFasts.setOnClickListener {
            if (keptFasts > 0) {
                keptFasts--
                saveCounts()
                updateCounterUi()
            }
        }

        binding.btnIncMissed.setOnClickListener {
            missedFasts++
            saveCounts()
            updateCounterUi()
        }

        binding.btnDecMissed.setOnClickListener {
            if (missedFasts > 0) {
                missedFasts--
                saveCounts()
                updateCounterUi()
            }
        }

        startCountdownTimer()
    }

    private fun startCountdownTimer() {
        val now = Calendar.getInstance()

        // Estimated Bangladesh standard times: Sehri 04:20 AM, Iftar 06:30 PM (18:30)
        val sehriCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
        }

        val iftarCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }

        val targetCal: Calendar
        val label: String

        if (now.before(sehriCal)) {
            targetCal = sehriCal
            label = "সাহরীর আর বাকি: "
        } else if (now.before(iftarCal)) {
            targetCal = iftarCal
            label = "ইফতারের আর বাকি: "
        } else {
            // Next day sehri
            sehriCal.add(Calendar.DAY_OF_YEAR, 1)
            targetCal = sehriCal
            label = "আগামীকালের সাহরীর আর বাকি: "
        }

        val diff = targetCal.timeInMillis - now.timeInMillis

        timer?.cancel()
        timer = object : CountDownTimer(diff, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (1000 * 60 * 60)
                val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                val seconds = (millisUntilFinished % (1000 * 60)) / 1000

                val timeStr = String.format(Locale.getDefault(), "%02d ঘন্টা %02d মিনিট %02d সেকেন্ড", hours, minutes, seconds)
                binding.tvFastingCountdown.text = "$label$timeStr"
            }

            override fun onFinish() {
                binding.tvFastingCountdown.text = "সময় হয়েছে!"
            }
        }.start()
    }

    private fun loadSavedCounts() {
        val prefs = getSharedPreferences("IslamicRamadanPrefs", Context.MODE_PRIVATE)
        keptFasts = prefs.getInt("kept_fasts", 0)
        missedFasts = prefs.getInt("missed_fasts", 0)
    }

    private fun saveCounts() {
        val prefs = getSharedPreferences("IslamicRamadanPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("kept_fasts", keptFasts)
            .putInt("missed_fasts", missedFasts)
            .apply()
    }

    private fun updateCounterUi() {
        binding.tvKeptFastsCount.text = keptFasts.toString()
        binding.tvMissedFastsCount.text = missedFasts.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
