package com.hafij.islamicalarm.tahajjud

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.AlarmScheduler
import com.hafij.islamicalarm.data.AlarmItem
import com.hafij.islamicalarm.data.AlarmStore
import com.hafij.islamicalarm.databinding.ActivityTahajjudCalculatorBinding
import com.hafij.islamicalarm.prayertimes.DistrictData
import com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TahajjudCalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTahajjudCalculatorBinding
    private lateinit var alarmStore: AlarmStore
    private var tahajjudAlarmCal: Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTahajjudCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmStore = AlarmStore(this)

        binding.toolbarTahajjud.setNavigationOnClickListener {
            finish()
        }

        calculateAndDisplayTahajjud()

        binding.btnSetTahajjudAlarm.setOnClickListener {
            scheduleTahajjudAlarm()
        }
    }

    private fun calculateAndDisplayTahajjud() {
        val now = Calendar.getInstance()
        val district = DistrictData.getDefaultDistrict()
        val schedule = PrayerTimeCalculator.calculate(now, district)

        val maghribCal = schedule.maghribCal
        val fajrCal = (schedule.fajrCal.clone() as Calendar).apply {
            if (before(maghribCal)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val totalNightDurationMillis = fajrCal.timeInMillis - maghribCal.timeInMillis
        val oneThirdDurationMillis = totalNightDurationMillis / 3

        val firstThirdEndMillis = maghribCal.timeInMillis + oneThirdDurationMillis
        val middleThirdEndMillis = maghribCal.timeInMillis + (2 * oneThirdDurationMillis)
        val lastThirdStartMillis = middleThirdEndMillis
        val lastThirdEndMillis = fajrCal.timeInMillis

        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        fun fmt(millis: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            var hour = c.get(Calendar.HOUR)
            if (hour == 0) hour = 12
            val min = c.get(Calendar.MINUTE)
            val amPm = if (c.get(Calendar.AM_PM) == Calendar.AM) "ভোর" else "রাত"
            val eng = String.format(Locale.US, "%02d:%02d", hour, min)
            return "$amPm ${PrayerTimeCalculator.toBengaliDigits(eng)}"
        }

        val firstThirdStr = "${fmt(maghribCal.timeInMillis)} হতে ${fmt(firstThirdEndMillis)}"
        val middleThirdStr = "${fmt(firstThirdEndMillis)} হতে ${fmt(middleThirdEndMillis)}"
        val lastThirdStr = "${fmt(lastThirdStartMillis)} হতে ${fmt(lastThirdEndMillis)}"

        binding.tvFirstThirdTime.text = firstThirdStr
        binding.tvMiddleThirdTime.text = middleThirdStr
        binding.tvLastThirdTime.text = lastThirdStr

        binding.tvTahajjudBestTime.text = lastThirdStr

        val durationHours = oneThirdDurationMillis / (1000 * 60 * 60)
        val durationMins = (oneThirdDurationMillis % (1000 * 60 * 60)) / (1000 * 60)
        val bnDurHours = PrayerTimeCalculator.toBengaliNumber(durationHours.toInt())
        val bnDurMins = PrayerTimeCalculator.toBengaliNumber(durationMins.toInt())
        binding.tvTahajjudDuration.text = "রাতের শেষ তৃতীয়াংশ ($bnDurHours ঘণ্টা $bnDurMins মিনিট সময়)"

        // Suggested alarm time: 30 minutes after the start of last third
        tahajjudAlarmCal = Calendar.getInstance().apply {
            timeInMillis = lastThirdStartMillis + (15 * 60 * 1000)
        }
    }

    private fun scheduleTahajjudAlarm() {
        val target = tahajjudAlarmCal ?: return
        val hour = target.get(Calendar.HOUR_OF_DAY)
        val minute = target.get(Calendar.MINUTE)

        val newAlarm = AlarmItem(
            hour = hour,
            minute = minute,
            label = "তাহাজ্জুদ ও দোয়া",
            lockDurationMinutes = 15,
            isRepeatDaily = true,
            isEnabled = true
        )

        alarmStore.addAlarm(newAlarm)
        val triggerMillis = AlarmScheduler.scheduleAlarm(this, newAlarm)
        val toastMsg = AlarmScheduler.getFormattedNextRingTime(this, triggerMillis)
        Toast.makeText(this, "তাহাজ্জুদের এলার্ম সেট হয়েছে!\n$toastMsg", Toast.LENGTH_LONG).show()
    }
}
