package com.hafij.islamicalarm.quran

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.hafij.islamicalarm.databinding.ActivityQuranBinding
import java.io.File
import java.util.Locale

class QuranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuranBinding
    private lateinit var surahAdapter: SurahAdapter
    private lateinit var paraAdapter: ParaAdapter

    private var activeTabIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val audioProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    val progress = (mp.currentPosition.toFloat() / mp.duration * 100).toInt()
                    binding.pbAudioProgress.progress = progress
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    companion object {
        private var mediaPlayer: MediaPlayer? = null
        private var currentPlayingSurah: Surah? = null
        private var isAudioPlaying = false
        private var quranTts: TextToSpeech? = null
        private var isTtsMode = false

        fun stopAudioGlobal() {
            if (isTtsMode) {
                try {
                    quranTts?.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isTtsMode = false
            }
            mediaPlayer?.let { mp ->
                try {
                    mp.setOnPreparedListener(null)
                    mp.setOnCompletionListener(null)
                    mp.setOnErrorListener(null)
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                    mp.reset()
                    mp.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            mediaPlayer = null
            currentPlayingSurah = null
            isAudioPlaying = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarQuran)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarQuran.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        initTts()

        setupSurahRecyclerView()
        setupParaRecyclerView()
        setupSearch()
        setupTabs()
        setupAudioPlayerBar()
        setupBookmarkCard()
    }

    override fun onResume() {
        super.onResume()
        updateLastReadCard()
        updatePlayerUiState()
    }

    private fun setupBookmarkCard() {
        updateLastReadCard()
        val openLastRead: () -> Unit = {
            val prefs = getSharedPreferences("IslamicAlarmQuranPrefs", Context.MODE_PRIVATE)
            val lastSurahId = prefs.getInt("last_read_surah_id", 1)
            val intent = Intent(this, SurahDetailActivity::class.java).apply {
                putExtra("EXTRA_SURAH_ID", lastSurahId)
            }
            startActivity(intent)
        }
        binding.btnContinueLastRead.setOnClickListener { openLastRead() }
        binding.cardLastRead.setOnClickListener { openLastRead() }
    }

    private fun updateLastReadCard() {
        val prefs = getSharedPreferences("IslamicAlarmQuranPrefs", Context.MODE_PRIVATE)
        val lastSurahId = prefs.getInt("last_read_surah_id", 1)
        val surah = QuranRepository.surahList.find { it.id == lastSurahId } ?: QuranRepository.surahList.first()
        binding.tvLastReadTitle.text = "সুরা ${surah.nameBangla} (${surah.nameArabic}) • ${toBengaliNumber(surah.totalAyahs)} আয়াত • ${surah.revelationType}"
    }

    private fun setupSurahRecyclerView() {
        surahAdapter = SurahAdapter(
            surahList = QuranRepository.surahList,
            onPlayAudio = { surah ->
                togglePlayAudio(surah)
            },
            onReadPage = { surah ->
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra("EXTRA_SURAH_ID", surah.id)
                }
                startActivity(intent)
            }
        )

        binding.rvSurahList.layoutManager = LinearLayoutManager(this)
        binding.rvSurahList.adapter = surahAdapter
    }

    private fun setupParaRecyclerView() {
        paraAdapter = ParaAdapter(
            paraList = QuranRepository.paraList,
            onParaClick = { para ->
                val surah = QuranRepository.getSurahForPage(para.startPage)
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra("EXTRA_SURAH_ID", surah.id)
                }
                startActivity(intent)
            }
        )

        binding.rvParaList.layoutManager = LinearLayoutManager(this)
        binding.rvParaList.adapter = paraAdapter
    }

    private fun setupSearch() {
        binding.etSearchSurah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchSurah.text?.clear()
            hideKeyboard()
        }
    }

    private fun performSearch(query: String) {
        if (activeTabIndex == 0) {
            filterSurahList(query)
        } else {
            filterParaList(query)
        }
    }

    private fun filterSurahList(query: String) {
        if (query.isEmpty()) {
            surahAdapter.updateData(QuranRepository.surahList)
            return
        }

        val filtered = QuranRepository.surahList.filter { surah ->
            surah.nameBangla.contains(query, ignoreCase = true) ||
                    surah.nameEnglish.contains(query, ignoreCase = true) ||
                    surah.nameArabic.contains(query, ignoreCase = true) ||
                    surah.meaningBangla.contains(query, ignoreCase = true) ||
                    surah.id.toString() == query ||
                    surah.paraNumber.toString() == query ||
                    toBengaliNumber(surah.id) == query ||
                    toBengaliNumber(surah.paraNumber) == query
        }

        surahAdapter.updateData(filtered)
    }

    private fun filterParaList(query: String) {
        if (query.isEmpty()) {
            paraAdapter.updateData(QuranRepository.paraList)
            return
        }

        val filtered = QuranRepository.paraList.filter { para ->
            para.nameBangla.contains(query, ignoreCase = true) ||
                    para.nameArabic.contains(query, ignoreCase = true) ||
                    para.meaningBangla.contains(query, ignoreCase = true) ||
                    para.id.toString() == query ||
                    toBengaliNumber(para.id) == query
        }

        paraAdapter.updateData(filtered)
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                activeTabIndex = tab?.position ?: 0
                when (activeTabIndex) {
                    0 -> {
                        binding.layoutSurahTab.visibility = View.VISIBLE
                        binding.layoutParaTab.visibility = View.GONE
                        binding.etSearchSurah.hint = "সুরা বা নম্বর দিয়ে খুঁজুন..."
                    }
                    1 -> {
                        binding.layoutSurahTab.visibility = View.GONE
                        binding.layoutParaTab.visibility = View.VISIBLE
                        binding.etSearchSurah.hint = "পারার নাম বা নম্বর দিয়ে খুঁজুন..."
                    }
                }
                val currentQuery = binding.etSearchSurah.text?.toString()?.trim() ?: ""
                performSearch(currentQuery)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun updatePlayerUiState() {
        val surah = currentPlayingSurah
        if (surah != null && (isAudioPlaying || mediaPlayer != null || isTtsMode)) {
            binding.cardAudioPlayer.visibility = View.VISIBLE
            binding.tvAudioSurahTitle.text = "সুরা ${surah.nameBangla} (${surah.nameArabic})"
            if (isAudioPlaying) {
                binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_pause)
                surahAdapter.setCurrentlyPlayingId(surah.id)
                handler.post(audioProgressRunnable)
            } else {
                binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_play)
                surahAdapter.setCurrentlyPlayingId(null)
            }
        } else {
            binding.cardAudioPlayer.visibility = View.GONE
            surahAdapter.setCurrentlyPlayingId(null)
        }
    }

    private fun setupAudioPlayerBar() {
        binding.btnAudioToggle.setOnClickListener {
            if (isTtsMode) {
                if (quranTts?.isSpeaking == true) {
                    quranTts?.stop()
                    isAudioPlaying = false
                    binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_play)
                    surahAdapter.setCurrentlyPlayingId(null)
                } else {
                    currentPlayingSurah?.let { playTtsSurah(it) }
                }
            } else {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.pause()
                        isAudioPlaying = false
                        binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_play)
                        surahAdapter.setCurrentlyPlayingId(null)
                    } else {
                        mp.start()
                        isAudioPlaying = true
                        binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_pause)
                        surahAdapter.setCurrentlyPlayingId(currentPlayingSurah?.id)
                        handler.post(audioProgressRunnable)
                    }
                }
            }
        }

        binding.btnAudioClose.setOnClickListener {
            stopAudio()
        }
    }

    private fun initTts() {
        if (quranTts == null) {
            quranTts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = quranTts?.setLanguage(Locale("ar"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        quranTts?.setLanguage(Locale("ar", "SA"))
                    }
                }
            }
        }
    }

    private fun getAudioFile(surahId: Int): File {
        val dir = File(filesDir, "quran_audio")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "surah_$surahId.mp3")
    }

    private fun togglePlayAudio(surah: Surah) {
        if (currentPlayingSurah?.id == surah.id && (mediaPlayer != null || isTtsMode)) {
            try {
                if (isTtsMode) {
                    if (quranTts?.isSpeaking == true) {
                        quranTts?.stop()
                        isAudioPlaying = false
                        binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_play)
                        surahAdapter.setCurrentlyPlayingId(null)
                    } else {
                        playTtsSurah(surah)
                    }
                } else if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.pause()
                    isAudioPlaying = false
                    binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_play)
                    surahAdapter.setCurrentlyPlayingId(null)
                } else {
                    mediaPlayer!!.start()
                    isAudioPlaying = true
                    binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_pause)
                    surahAdapter.setCurrentlyPlayingId(surah.id)
                    handler.post(audioProgressRunnable)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopAudio()
            }
            return
        }

        stopAudio()

        currentPlayingSurah = surah
        binding.cardAudioPlayer.visibility = View.VISIBLE
        binding.tvAudioSurahTitle.text = "সুরা ${surah.nameBangla} (${surah.nameArabic})"

        val localFile = getAudioFile(surah.id)
        if (localFile.exists() && localFile.length() > 1024) {
            binding.tvAudioReciter.text = "ক্বারী মিশারী রশিদ (অফলাইন)"
            binding.pbAudioProgress.isIndeterminate = false
            startMediaPlayerWithFile(surah, localFile)
        } else {
            playTtsSurah(surah)
            startMediaPlayerWithUrl(surah, useFallback = false)
        }
    }

    private fun playTtsSurah(surah: Surah) {
        isTtsMode = true
        isAudioPlaying = true
        binding.tvAudioReciter.text = "অফলাইন অডিও প্লেয়ার"
        binding.pbAudioProgress.isIndeterminate = true
        binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_pause)
        surahAdapter.setCurrentlyPlayingId(surah.id)

        val textToSpeak = "سورة ${surah.nameArabic}. بسم الله الرحمن الرحيم"
        quranTts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (isTtsMode && isAudioPlaying && currentPlayingSurah?.id == surah.id) {
                    handler.postDelayed({
                        try {
                            quranTts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "quran_tts_${surah.id}")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, 1000)
                }
            }
            override fun onError(utteranceId: String?) {}
        })
        quranTts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "quran_tts_${surah.id}")
    }

    private fun startMediaPlayerWithFile(surah: Surah, file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                isLooping = true
                setOnPreparedListener { mp ->
                    try {
                        binding.pbAudioProgress.isIndeterminate = false
                        binding.pbAudioProgress.progress = 0
                        mp.start()
                        isAudioPlaying = true
                        binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_pause)
                        surahAdapter.setCurrentlyPlayingId(surah.id)
                        handler.post(audioProgressRunnable)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    stopAudio()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
        }
    }

    private fun startMediaPlayerWithUrl(surah: Surah, useFallback: Boolean) {
        val urlToPlay = if (useFallback) surah.fallbackAudioUrl else surah.audioUrl

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(urlToPlay)
                isLooping = true
                setOnPreparedListener { mp ->
                    try {
                        if (isTtsMode) {
                            try { quranTts?.stop() } catch (e: Exception) { e.printStackTrace() }
                            isTtsMode = false
                        }
                        binding.pbAudioProgress.isIndeterminate = false
                        binding.pbAudioProgress.progress = 0
                        binding.tvAudioReciter.text = "ক্বারী মিশারী রশিদ (অনলাইন)"
                        mp.start()
                        isAudioPlaying = true
                        binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_pause)
                        surahAdapter.setCurrentlyPlayingId(surah.id)
                        handler.post(audioProgressRunnable)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (!useFallback) {
                        startMediaPlayerWithUrl(surah, useFallback = true)
                    } else {
                        playTtsSurah(surah)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!useFallback) {
                startMediaPlayerWithUrl(surah, useFallback = true)
            } else {
                playTtsSurah(surah)
            }
        }
    }

    private fun stopAudio() {
        stopAudioGlobal()
        handler.removeCallbacks(audioProgressRunnable)
        binding.cardAudioPlayer.visibility = View.GONE
        surahAdapter.setCurrentlyPlayingId(null)
    }

    private fun toBengaliNumber(number: Int): String {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '⑧', '৯')
        val str = number.toString()
        val sb = StringBuilder()
        for (ch in str) {
            if (ch.isDigit()) {
                val idx = ch - '0'
                val digit = if (idx == 8) '৮' else banglaDigits[idx]
                sb.append(digit)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(audioProgressRunnable)
    }
}
