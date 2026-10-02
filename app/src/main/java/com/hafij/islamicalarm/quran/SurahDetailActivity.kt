package com.hafij.islamicalarm.quran

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivitySurahDetailBinding
import kotlinx.coroutines.launch

class SurahDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySurahDetailBinding
    private lateinit var ayahAdapter: AyahAdapter
    private var surah: Surah? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingAudio = false
    private var fontScaleDelta = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurahDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val surahId = intent.getIntExtra("EXTRA_SURAH_ID", 1)
        surah = QuranRepository.surahList.find { it.id == surahId } ?: QuranRepository.surahList.first()

        setupToolbar()
        setupFontControls()
        setupAudioPlayer()
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

        binding.tvHeaderSurahArabic.text = "سورة ${s.nameArabic}"
        binding.tvHeaderSurahDetails.text = "অর্থ: ${s.meaningBangla} • ${toBengaliNumber(s.totalAyahs)}টি আয়াত • ${s.revelationType} • পারা ${toBengaliNumber(s.paraNumber)}"
        if (s.id == 9) {
            binding.layoutBismillah.visibility = View.GONE
        } else {
            binding.layoutBismillah.visibility = View.VISIBLE
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

    private fun setupAudioPlayer() {
        val s = surah ?: return
        binding.btnPlaySurahAudio.setOnClickListener {
            if (isPlayingAudio) {
                pauseAudio()
            } else {
                playAudio(s.audioUrl, s.fallbackAudioUrl)
            }
        }
    }

    private fun playAudio(url: String, fallbackUrl: String) {
        stopAudio()
        binding.btnPlaySurahAudio.text = "লোড হচ্ছে..."

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    mp.start()
                    isPlayingAudio = true
                    binding.btnPlaySurahAudio.text = "বিরতি (Pause)"
                    Toast.makeText(this@SurahDetailActivity, "ক্বারী মিশারী আল-আফাসীর কণ্ঠে তিলাওয়াত চলছে...", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    isPlayingAudio = false
                    binding.btnPlaySurahAudio.text = "অডিও শুনুন"
                }
                setOnErrorListener { _, _, _ ->
                    if (fallbackUrl.isNotBlank() && url != fallbackUrl) {
                        playAudio(fallbackUrl, "")
                    } else {
                        isPlayingAudio = false
                        binding.btnPlaySurahAudio.text = "অডিও শুনুন"
                        Toast.makeText(this@SurahDetailActivity, "অডিও চালু করা যায়নি, ইন্টারনেট সংযোগ চেক করুন", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.btnPlaySurahAudio.text = "অডিও শুনুন"
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                isPlayingAudio = false
                binding.btnPlaySurahAudio.text = "শুনুন (Play)"
            }
        }
    }

    private fun stopAudio() {
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = null
        isPlayingAudio = false
        binding.btnPlaySurahAudio.text = "অডিও শুনুন"
    }

    private fun setupRecyclerView() {
        val s = surah ?: return
        ayahAdapter = AyahAdapter(emptyList(), s.nameBangla)
        binding.rvAyahs.layoutManager = LinearLayoutManager(this)
        binding.rvAyahs.adapter = ayahAdapter

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
            .putInt("last_read_page", s.startPage)
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

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }
}
