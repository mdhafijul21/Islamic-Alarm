package com.hafij.islamicalarm.tasbih

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityTasbihBinding

data class ZikrItem(val arabic: String, val bangla: String)

class TasbihActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTasbihBinding

    private var currentCount = 0
    private var totalCountToday = 0
    private var targetCount = 33 // 33, 100, 1000, or -1 (Unlimited)
    private var isSoundOn = true
    private var isVibrationOn = true

    private var toneGenerator: ToneGenerator? = null

    private val zikrList = listOf(
        ZikrItem("سُبْحَانَ ٱللَّهِ", "সুবহানাল্লাহ (আল্লাহ পবিত্র)"),
        ZikrItem("ٱلْحَمْدُ لِلَّهِ", "আলহামদুলিল্লাহ (সমস্ত প্রশংসা আল্লাহর)"),
        ZikrItem("ٱللَّهُ أَكْبَرُ", "আল্লাহু আকবার (আল্লাহ সবচেয়ে মহান)"),
        ZikrItem("لَا إِلٰهَ إِلَّا ٱللَّهُ", "লা ইলাহা ইল্লাল্লাহু (আল্লাহ ছাড়া কোনো ইলাহ নেই)"),
        ZikrItem("أَسْتَغْفِرُ ٱللَّهَ", "আস্তাগফিরুল্লাহ (আমি আল্লাহর ক্ষমা প্রার্থনা করছি)"),
        ZikrItem("لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِٱللَّهِ", "লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ"),
        ZikrItem("صَلَّى ٱللَّهُ عَلَيْهِ وَسَلَّمَ", "সাল্লাল্লাহু আলাইহি ওয়াসাল্লাম (দরূদ শরীফ)")
    )
    private var currentZikrIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasbihBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarTasbih.setNavigationOnClickListener {
            finish()
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        loadSavedState()
        updateUi()

        binding.tvSelectedZikrArabic.setOnClickListener { showZikrSelectionDialog() }
        binding.tvSelectedZikrBangla.setOnClickListener { showZikrSelectionDialog() }

        binding.chipGroupTarget.setOnCheckedStateChangeListener { _, checkedIds ->
            targetCount = when {
                checkedIds.contains(R.id.chip33) -> 33
                checkedIds.contains(R.id.chip100) -> 100
                checkedIds.contains(R.id.chip1000) -> 1000
                else -> -1
            }
            saveState()
            updateUi()
        }

        binding.btnCountTap.setOnClickListener {
            performTap()
        }

        binding.btnSoundToggle.setOnClickListener {
            isSoundOn = !isSoundOn
            saveState()
            updateUi()
        }

        binding.btnVibrationToggle.setOnClickListener {
            isVibrationOn = !isVibrationOn
            saveState()
            updateUi()
        }

        binding.btnResetCounter.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("গণনা রিসেট করুন")
                .setMessage("আপনি কি বর্তমান গণনাকৃত সংখ্যা রিসেট করতে চান?")
                .setPositiveButton("হ্যাঁ") { _, _ ->
                    currentCount = 0
                    saveState()
                    updateUi()
                }
                .setNegativeButton("না", null)
                .show()
        }
    }

    private fun showZikrSelectionDialog() {
        val names = zikrList.map { "${it.bangla}\n${it.arabic}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("জিকির নির্বাচন করুন")
            .setItems(names) { _, which ->
                currentZikrIndex = which
                currentCount = 0
                saveState()
                updateUi()
            }
            .show()
    }

    private fun performTap() {
        currentCount++
        totalCountToday++

        if (isSoundOn) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isVibrationOn) {
            vibrateDevice(50)
        }

        if (targetCount > 0 && currentCount == targetCount) {
            // Target achieved celebration!
            if (isVibrationOn) {
                vibrateDevice(300)
            }
            Toast.makeText(
                this,
                "মাশাআল্লাহ! ${targetCount} বার জিকির সম্পন্ন হয়েছে",
                Toast.LENGTH_LONG
            ).show()
        }

        saveState()
        updateUi()
    }

    private fun vibrateDevice(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateUi() {
        val zikr = zikrList[currentZikrIndex]
        binding.tvSelectedZikrArabic.text = zikr.arabic
        binding.tvSelectedZikrBangla.text = zikr.bangla

        binding.tvCounterText.text = currentCount.toString()
        binding.tvTargetSubText.text = if (targetCount > 0) "টার্গেট: $targetCount" else "টার্গেট: অসীম"
        binding.tvTotalCountToday.text = "আজকের মোট পঠিত জিকির: $totalCountToday বার"

        binding.btnSoundToggle.text = if (isSoundOn) "শব্দ: চালু" else "শব্দ: বন্ধ"
        binding.btnVibrationToggle.text = if (isVibrationOn) "ভাইব্রেশন: চালু" else "ভাইব্রেশন: বন্ধ"

        when (targetCount) {
            33 -> binding.chip33.isChecked = true
            100 -> binding.chip100.isChecked = true
            1000 -> binding.chip1000.isChecked = true
            else -> binding.chipUnlimited.isChecked = true
        }
    }

    private fun loadSavedState() {
        val prefs = getSharedPreferences("IslamicTasbihPrefs", Context.MODE_PRIVATE)
        currentCount = prefs.getInt("current_count", 0)
        totalCountToday = prefs.getInt("total_count_today", 0)
        targetCount = prefs.getInt("target_count", 33)
        currentZikrIndex = prefs.getInt("zikr_index", 0)
        isSoundOn = prefs.getBoolean("sound_on", true)
        isVibrationOn = prefs.getBoolean("vibration_on", true)
    }

    private fun saveState() {
        val prefs = getSharedPreferences("IslamicTasbihPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("current_count", currentCount)
            .putInt("total_count_today", totalCountToday)
            .putInt("target_count", targetCount)
            .putInt("zikr_index", currentZikrIndex)
            .putBoolean("sound_on", isSoundOn)
            .putBoolean("vibration_on", isVibrationOn)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        toneGenerator = null
    }
}
