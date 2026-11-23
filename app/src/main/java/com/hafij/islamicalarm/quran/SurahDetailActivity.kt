package com.hafij.islamicalarm.quran

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.databinding.ActivitySurahDetailBinding
import kotlinx.coroutines.launch

class SurahDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySurahDetailBinding
    private lateinit var ayahAdapter: AyahAdapter
    private var surah: Surah? = null
    private var fontScaleDelta = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurahDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val surahId = intent.getIntExtra("EXTRA_SURAH_ID", 1)
        surah = QuranRepository.surahList.find { it.id == surahId } ?: QuranRepository.surahList.first()

        setupToolbar()
        setupFontControls()
        setupRecyclerView()
        loadAyahs()
        saveLastRead()
    }

    private fun setupToolbar() {
        val s = surah ?: return
        binding.toolbarSurah.title = "সুরা ${s.nameBangla} (${s.nameArabic})"
        binding.toolbarSurah.subtitle = "${s.revelationType} • ${toBengaliNumber(s.totalAyahs)}টি আয়াত • পারা ${toBengaliNumber(s.paraNumber)}"
        binding.toolbarSurah.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupFontControls() {
        binding.btnSurahFontLarger.setOnClickListener {
            if (fontScaleDelta < 8f) {
                fontScaleDelta += 2f
                ayahAdapter.fontScaleDelta = fontScaleDelta
                ayahAdapter.notifyDataSetChanged()
                binding.tvSurahFontSizeIndicator.text = "A+"
            }
        }

        binding.btnSurahFontSmaller.setOnClickListener {
            if (fontScaleDelta > -4f) {
                fontScaleDelta -= 2f
                ayahAdapter.fontScaleDelta = fontScaleDelta
                ayahAdapter.notifyDataSetChanged()
                binding.tvSurahFontSizeIndicator.text = if (fontScaleDelta == 0f) "A" else "A-"
            }
        }
    }

    private fun setupRecyclerView() {
        val s = surah ?: return
        ayahAdapter = AyahAdapter(emptyList(), s.nameBangla)
        val headerAdapter = SurahHeaderAdapter(
            arabicName = "سورة ${s.nameArabic}",
            detailsText = "অর্থ: ${s.meaningBangla} • ${toBengaliNumber(s.totalAyahs)}টি আয়াত • ${s.revelationType} • পারা ${toBengaliNumber(s.paraNumber)}",
            showBismillah = s.id != 9
        )
        binding.rvAyahs.layoutManager = LinearLayoutManager(this)
        binding.rvAyahs.adapter = ConcatAdapter(headerAdapter, ayahAdapter)

        binding.btnRetryLoadSurah.setOnClickListener {
            loadAyahs()
        }
    }

    private fun loadAyahs() {
        val s = surah ?: return
        binding.pbLoadingSurah.visibility = View.VISIBLE
        binding.layoutSurahError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val ayahs = QuranRepository.fetchSurahAyahs(this@SurahDetailActivity, s.id)
                binding.pbLoadingSurah.visibility = View.GONE
                if (ayahs.isNotEmpty()) {
                    ayahAdapter.updateList(ayahs)
                } else {
                    binding.layoutSurahError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.pbLoadingSurah.visibility = View.GONE
                binding.layoutSurahError.visibility = View.VISIBLE
            }
        }
    }

    private fun saveLastRead() {
        val s = surah ?: return
        val prefs = getSharedPreferences("IslamicAlarmQuranPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("last_read_surah_id", s.id)
            .putString("last_read_surah_name", s.nameBangla)
            .putInt("last_read_page", QuranRepository.getHafeziStartPageForSurah(s.id))
            .apply()
    }

    private fun toBengaliNumber(num: Int): String {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val str = num.toString()
        val sb = StringBuilder()
        for (ch in str) {
            if (ch.isDigit()) {
                sb.append(banglaDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
