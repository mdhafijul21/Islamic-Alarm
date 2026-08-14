package com.hafij.islamicalarm.amal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.databinding.ActivityAmalTrackerBinding
import com.hafij.islamicalarm.databinding.ItemAmalPrayerBinding
import com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AmalTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAmalTrackerBinding
    private lateinit var amalStore: AmalStore
    private var currentCal: Calendar = Calendar.getInstance()
    private lateinit var currentAmal: DailyAmal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAmalTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        amalStore = AmalStore(this)

        binding.toolbarAmal.setNavigationOnClickListener {
            finish()
        }

        setupPrayerItemNames()
        setupListeners()
        loadDataForCurrentDate()
    }

    private fun setupPrayerItemNames() {
        binding.itemFajr.tvAmalPrayerName.text = "১. ফজর সালাত"
        binding.itemDhuhr.tvAmalPrayerName.text = "২. যোহর সালাত"
        binding.itemAsr.tvAmalPrayerName.text = "৩. আসর সালাত"
        binding.itemMaghrib.tvAmalPrayerName.text = "৪. মাগরিব সালাত"
        binding.itemIsha.tvAmalPrayerName.text = "৫. ইশা সালাত"
    }

    private fun setupListeners() {
        binding.btnPrevDay.setOnClickListener {
            currentCal.add(Calendar.DAY_OF_YEAR, -1)
            loadDataForCurrentDate()
        }

        binding.btnNextDay.setOnClickListener {
            currentCal.add(Calendar.DAY_OF_YEAR, 1)
            loadDataForCurrentDate()
        }

        // Setup radio listeners for each prayer
        setupPrayerRadio(binding.itemFajr) { status ->
            currentAmal.fajrStatus = status
            saveAndRefreshUi()
        }
        setupPrayerRadio(binding.itemDhuhr) { status ->
            currentAmal.dhuhrStatus = status
            saveAndRefreshUi()
        }
        setupPrayerRadio(binding.itemAsr) { status ->
            currentAmal.asrStatus = status
            saveAndRefreshUi()
        }
        setupPrayerRadio(binding.itemMaghrib) { status ->
            currentAmal.maghribStatus = status
            saveAndRefreshUi()
        }
        setupPrayerRadio(binding.itemIsha) { status ->
            currentAmal.ishaStatus = status
            saveAndRefreshUi()
        }

        // Setup checkboxes
        binding.cbTahajjud.setOnCheckedChangeListener { _, isChecked ->
            currentAmal.tahajjud = isChecked
            saveAndRefreshUi()
        }
        binding.cbIshraq.setOnCheckedChangeListener { _, isChecked ->
            currentAmal.ishraq = isChecked
            saveAndRefreshUi()
        }
        binding.cbQuran.setOnCheckedChangeListener { _, isChecked ->
            currentAmal.quranRecitation = isChecked
            saveAndRefreshUi()
        }
        binding.cbZikr.setOnCheckedChangeListener { _, isChecked ->
            currentAmal.morningEveningZikr = isChecked
            saveAndRefreshUi()
        }
        binding.cbIstighfar.setOnCheckedChangeListener { _, isChecked ->
            currentAmal.istighfarDurood = isChecked
            saveAndRefreshUi()
        }
        binding.cbMulk.setOnCheckedChangeListener { _, isChecked ->
            currentAmal.surahMulk = isChecked
            saveAndRefreshUi()
        }
        binding.cbCharity.setOnCheckedChangeListener { _, isChecked ->
            currentAmal.charity = isChecked
            saveAndRefreshUi()
        }
    }

    private fun setupPrayerRadio(itemBinding: ItemAmalPrayerBinding, onStatusChanged: (Int) -> Unit) {
        itemBinding.rgAmalStatus.setOnCheckedChangeListener { _, checkedId ->
            val status = when (checkedId) {
                itemBinding.rbJamat.id -> 1
                itemBinding.rbAlone.id -> 2
                itemBinding.rbQaza.id -> 3
                else -> 0
            }
            updateStatusLabel(itemBinding, status)
            onStatusChanged(status)
        }
    }

    private fun updateStatusLabel(itemBinding: ItemAmalPrayerBinding, status: Int) {
        itemBinding.tvAmalStatusLabel.text = when (status) {
            1 -> "জামাতে আদায় (+১২)"
            2 -> "একাকী আদায় (+১০)"
            3 -> "কাজা আদায় (+৬)"
            else -> "আদায় হয়নি"
        }
    }

    private fun loadDataForCurrentDate() {
        val dateKey = AmalStore.getDateKey(currentCal)
        currentAmal = amalStore.getAmalForDate(dateKey)

        // Date Display
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("bn", "BD"))
        val formattedDate = sdf.format(currentCal.time)
        val todayKey = AmalStore.getDateKey(Calendar.getInstance())

        val prefix = when (dateKey) {
            todayKey -> "আজ"
            AmalStore.getDateKey(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "গতকাল"
            AmalStore.getDateKey(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }) -> "আগামীকাল"
            else -> ""
        }
        val dateText = if (prefix.isNotEmpty()) "$prefix, $formattedDate" else formattedDate
        binding.tvAmalDate.text = PrayerTimeCalculator.toBengaliDigits(dateText)

        // Bind radio states
        bindPrayerStatus(binding.itemFajr, currentAmal.fajrStatus)
        bindPrayerStatus(binding.itemDhuhr, currentAmal.dhuhrStatus)
        bindPrayerStatus(binding.itemAsr, currentAmal.asrStatus)
        bindPrayerStatus(binding.itemMaghrib, currentAmal.maghribStatus)
        bindPrayerStatus(binding.itemIsha, currentAmal.ishaStatus)

        // Bind Checkboxes
        binding.cbTahajjud.isChecked = currentAmal.tahajjud
        binding.cbIshraq.isChecked = currentAmal.ishraq
        binding.cbQuran.isChecked = currentAmal.quranRecitation
        binding.cbZikr.isChecked = currentAmal.morningEveningZikr
        binding.cbIstighfar.isChecked = currentAmal.istighfarDurood
        binding.cbMulk.isChecked = currentAmal.surahMulk
        binding.cbCharity.isChecked = currentAmal.charity

        updateScoreAndStreak()
    }

    private fun bindPrayerStatus(itemBinding: ItemAmalPrayerBinding, status: Int) {
        when (status) {
            1 -> itemBinding.rbJamat.isChecked = true
            2 -> itemBinding.rbAlone.isChecked = true
            3 -> itemBinding.rbQaza.isChecked = true
            else -> itemBinding.rbNone.isChecked = true
        }
        updateStatusLabel(itemBinding, status)
    }

    private fun saveAndRefreshUi() {
        amalStore.saveAmal(currentAmal)
        updateScoreAndStreak()
    }

    private fun updateScoreAndStreak() {
        val score = currentAmal.calculateScore()
        binding.progressBarAmal.progress = score
        val bnScore = PrayerTimeCalculator.toBengaliNumber(score)
        binding.tvAmalScoreLabel.text = "আজকের আমল পূর্ণতাঃ $bnScore%"

        val streak = amalStore.getStreak()
        val bnStreak = PrayerTimeCalculator.toBengaliNumber(streak)
        binding.tvStreakCount.text = "🔥 $bnStreak দিন ধারাবাহিক"
    }
}
