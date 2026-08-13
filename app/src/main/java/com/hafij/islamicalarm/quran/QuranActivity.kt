package com.hafij.islamicalarm.quran

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityQuranBinding
import kotlinx.coroutines.launch

class QuranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuranBinding
    private lateinit var surahAdapter: SurahAdapter
    private lateinit var paraAdapter: ParaAdapter

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingSurah: Surah? = null
    private var isAudioPlaying = false

    private var currentPageNumber = 1
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarQuran)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarQuran.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupSurahRecyclerView()
        setupParaRecyclerView()
        setupSearch()
        setupTabs()
        setupPageControls()
        setupAudioPlayerBar()

        // Load page 1 data initially
        loadPage(1)
    }

    private fun setupSurahRecyclerView() {
        surahAdapter = SurahAdapter(
            surahList = QuranRepository.surahList,
            onPlayAudio = { surah ->
                togglePlayAudio(surah)
            },
            onReadPage = { surah ->
                currentPageNumber = surah.startPage
                binding.tabLayout.getTabAt(2)?.select()
                loadPage(currentPageNumber)
            }
        )

        binding.rvSurahList.layoutManager = LinearLayoutManager(this)
        binding.rvSurahList.adapter = surahAdapter
    }

    private fun setupParaRecyclerView() {
        paraAdapter = ParaAdapter(
            paraList = QuranRepository.paraList,
            onParaClick = { para ->
                currentPageNumber = para.startPage
                binding.tabLayout.getTabAt(2)?.select()
                loadPage(currentPageNumber)
            }
        )

        binding.rvParaList.layoutManager = LinearLayoutManager(this)
        binding.rvParaList.adapter = paraAdapter
    }

    private fun setupSearch() {
        binding.etSearchSurah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSurahList(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
                    surah.id.toString() == query ||
                    surah.paraNumber.toString() == query ||
                    toBengaliNumber(surah.id) == query ||
                    toBengaliNumber(surah.paraNumber) == query
        }

        surahAdapter.updateData(filtered)
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutSurahTab.visibility = View.VISIBLE
                        binding.layoutParaTab.visibility = View.GONE
                        binding.layoutPageTab.visibility = View.GONE
                    }
                    1 -> {
                        binding.layoutSurahTab.visibility = View.GONE
                        binding.layoutParaTab.visibility = View.VISIBLE
                        binding.layoutPageTab.visibility = View.GONE
                    }
                    2 -> {
                        binding.layoutSurahTab.visibility = View.GONE
                        binding.layoutParaTab.visibility = View.GONE
                        binding.layoutPageTab.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupPageControls() {
        binding.btnNextPage.setOnClickListener {
            if (currentPageNumber < 604) {
                currentPageNumber++
                loadPage(currentPageNumber)
            } else {
                Toast.makeText(this, "এটি কোরআনের শেষ পৃষ্ঠা", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPageNumber > 1) {
                currentPageNumber--
                loadPage(currentPageNumber)
            } else {
                Toast.makeText(this, "এটি কোরআনের প্রথম পৃষ্ঠা", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGoJump.setOnClickListener {
            val pageStr = binding.etPageNumberInput.text.toString().trim()
            val paraStr = binding.etParaNumberInput.text.toString().trim()

            hideKeyboard()

            if (pageStr.isNotEmpty()) {
                val p = pageStr.toIntOrNull()
                if (p != null && p in 1..604) {
                    loadPage(p)
                } else {
                    Toast.makeText(this, "১ থেকে ৬০৪ এর মধ্যে পৃষ্ঠা নম্বর দিন", Toast.LENGTH_SHORT).show()
                }
            } else if (paraStr.isNotEmpty()) {
                val para = paraStr.toIntOrNull()
                if (para != null && para in 1..30) {
                    val startP = QuranRepository.paraStartPages[para] ?: 1
                    loadPage(startP)
                } else {
                    Toast.makeText(this, "১ থেকে ৩০ এর মধ্যে পারা নম্বর দিন", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "পারা বা পৃষ্ঠা নম্বর লিখুন", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPage(pageNumber: Int) {
        currentPageNumber = pageNumber
        val juz = QuranRepository.getJuzForPage(pageNumber)
        val surah = QuranRepository.getSurahForPage(pageNumber)
        val para = QuranRepository.paraList.find { it.id == juz }

        // Update Nav Bar Title
        binding.tvPageHeader.text = "পৃষ্ঠা ${toBengaliNumber(pageNumber)} • পারা ${toBengaliNumber(juz)}"

        // Update Jump Controls Inputs
        binding.etParaNumberInput.setText(juz.toString())
        binding.etPageNumberInput.setText(pageNumber.toString())

        // Update Traditional Frame Header
        binding.tvFrameLeftInfo.text = "سورة ${surah.nameArabic}"
        binding.tvFramePageNumber.text = toBengaliNumber(pageNumber)
        binding.tvFrameRightInfo.text = "${para?.nameArabic ?: ""} ${toBengaliNumber(juz)}"

        binding.pbPageLoading.visibility = View.VISIBLE
        binding.layout15LinesContainer.removeAllViews()

        lifecycleScope.launch {
            val pageData = QuranRepository.fetchPageData(pageNumber)
            binding.pbPageLoading.visibility = View.GONE
            binding.layout15LinesContainer.removeAllViews()

            val inflater = LayoutInflater.from(this@QuranActivity)
            for (lineText in pageData.lines) {
                val lineView = inflater.inflate(R.layout.item_quran_line, binding.layout15LinesContainer, false)
                val tvLine = lineView.findViewById<TextView>(R.id.tvQuranLineText)
                tvLine.text = lineText
                binding.layout15LinesContainer.addView(lineView)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun setupAudioPlayerBar() {
        binding.btnAudioToggle.setOnClickListener {
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

        binding.btnAudioClose.setOnClickListener {
            stopAudio()
        }
    }

    private fun togglePlayAudio(surah: Surah) {
        if (currentPlayingSurah?.id == surah.id && mediaPlayer != null) {
            try {
                if (mediaPlayer!!.isPlaying) {
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
        binding.tvAudioSurahTitle.text = "চলছে: সুরা ${surah.nameBangla} (${surah.nameArabic})"
        binding.pbAudioProgress.progress = 0

        Toast.makeText(this, "সুরা ${surah.nameBangla} অডিও লোড হচ্ছে...", Toast.LENGTH_SHORT).show()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(surah.audioUrl)
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        isAudioPlaying = true
                        binding.btnAudioToggle.setImageResource(android.R.drawable.ic_media_pause)
                        surahAdapter.setCurrentlyPlayingId(surah.id)
                        handler.post(audioProgressRunnable)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnCompletionListener {
                    stopAudio()
                    Toast.makeText(this@QuranActivity, "সুরা পাঠ সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(this@QuranActivity, "অডিও চালাতে সমস্যা হয়েছে। ইন্টারনেট সংযোগ দেখুন।", Toast.LENGTH_SHORT).show()
                    stopAudio()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "অডিও চালু করতে ত্রুটি ঘটেছে", Toast.LENGTH_SHORT).show()
            stopAudio()
        }
    }

    private fun stopAudio() {
        handler.removeCallbacks(audioProgressRunnable)
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
        binding.cardAudioPlayer.visibility = View.GONE
        surahAdapter.setCurrentlyPlayingId(null)
    }

    private fun toBengaliNumber(number: Int): String {
        val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val str = number.toString()
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
