package com.hafij.islamicalarm.audio

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
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityAudioQuranBinding
import com.hafij.islamicalarm.quran.QuranActivity
import com.hafij.islamicalarm.quran.QuranRepository
import com.hafij.islamicalarm.quran.Surah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class AudioQuranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioQuranBinding
    private lateinit var surahAdapter: AudioSurahAdapter
    private var allSurahs: List<Surah> = QuranRepository.surahList
    private var filteredSurahs: List<Surah> = allSurahs

    private var currentReciter: Reciter = ReciterData.getDefaultReciter()
    private var currentPlayingSurah: Surah? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isAudioActive = false
    private var isRepeatOne = false
    private var tts: TextToSpeech? = null
    private var isTtsMode = false

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    val currentPos = mp.currentPosition
                    val totalDuration = mp.duration
                    if (totalDuration > 0) {
                        val progress = (currentPos.toFloat() / totalDuration * 100).toInt()
                        binding.sbSurahProgress.progress = progress
                        binding.tvPlayerCurrentTime.text = formatTime(currentPos)
                        binding.tvPlayerTotalDuration.text = formatTime(totalDuration)
                    }
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioQuranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarAudioQuran.setNavigationOnClickListener {
            finish()
        }

        initTts()
        setupReciterChips()
        setupRecyclerView()
        setupSearch()
        setupPlayerControls()
    }

    private fun setupReciterChips() {
        val prefs = getSharedPreferences("IslamicQuranAudioPrefs", Context.MODE_PRIVATE)
        val savedReciterId = prefs.getString("selected_reciter_id", "mishary") ?: "mishary"
        currentReciter = ReciterData.reciters.find { it.id == savedReciterId } ?: ReciterData.getDefaultReciter()

        ReciterData.reciters.forEach { reciter ->
            val chip = Chip(this).apply {
                text = "${reciter.nameBn} (${reciter.style})"
                isCheckable = true
                isChecked = (reciter.id == currentReciter.id)
                setChipBackgroundColorResource(R.color.surface)
                setTextColor(resources.getColor(R.color.text_primary, theme))
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currentReciter = reciter
                    prefs.edit().putString("selected_reciter_id", reciter.id).apply()
                    binding.tvPlayerReciterName.text = reciter.nameBn
                    Toast.makeText(this, "${reciter.nameBn} নির্বাচন করা হয়েছে", Toast.LENGTH_SHORT).show()

                    // If playing, switch audio source seamlessly
                    currentPlayingSurah?.let { surah ->
                        if (isAudioActive) {
                            playSurah(surah)
                        }
                    }
                }
            }
            binding.chipGroupReciters.addView(chip)
        }
    }

    private fun setupRecyclerView() {
        surahAdapter = AudioSurahAdapter(
            surahs = filteredSurahs,
            onPlayClick = { surah ->
                togglePlay(surah)
            },
            onDownloadClick = { surah ->
                downloadSurahOffline(surah)
            },
            onReadClick = { surah ->
                val intent = Intent(this, QuranActivity::class.java).apply {
                    putExtra("START_PAGE", surah.startPage)
                }
                startActivity(intent)
            }
        )

        binding.rvAudioSurahs.layoutManager = LinearLayoutManager(this)
        binding.rvAudioSurahs.adapter = surahAdapter
    }

    private fun setupSearch() {
        binding.etSearchAudioSurah.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSurahs(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterSurahs(query: String) {
        val qLower = query.lowercase(Locale.ROOT)
        filteredSurahs = if (qLower.isBlank()) {
            allSurahs
        } else {
            allSurahs.filter { surah ->
                surah.id.toString() == qLower ||
                        surah.nameBangla.lowercase(Locale.ROOT).contains(qLower) ||
                        surah.nameArabic.contains(qLower) ||
                        surah.nameEnglish.lowercase(Locale.ROOT).contains(qLower) ||
                        surah.meaningBangla.lowercase(Locale.ROOT).contains(qLower)
            }
        }
        surahAdapter.updateList(filteredSurahs)
        binding.tvSurahCountLabel.text = "📖 মোট ${filteredSurahs.size}টি সুরা পাওয়া গেছে"
    }

    private fun setupPlayerControls() {
        binding.btnPlayerPlayPause.setOnClickListener {
            currentPlayingSurah?.let { surah ->
                if (isAudioActive) {
                    pauseAudio()
                } else {
                    resumeAudio()
                }
            }
        }

        binding.btnPlayerNext.setOnClickListener {
            playNextSurah()
        }

        binding.btnPlayerPrev.setOnClickListener {
            playPreviousSurah()
        }

        binding.btnPlayerRepeat.setOnClickListener {
            isRepeatOne = !isRepeatOne
            if (isRepeatOne) {
                binding.btnPlayerRepeat.setColorFilter(resources.getColor(R.color.secondary, theme))
                Toast.makeText(this, "সুরা পুনরাবৃত্তি (Repeat 1) চালু", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnPlayerRepeat.setColorFilter(resources.getColor(R.color.text_secondary, theme))
                Toast.makeText(this, "ধারাবাহিক তিলাওয়াত (Auto Next) চালু", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPlayerMushaf.setOnClickListener {
            currentPlayingSurah?.let { surah ->
                val intent = Intent(this, QuranActivity::class.java).apply {
                    putExtra("START_PAGE", surah.startPage)
                }
                startActivity(intent)
            }
        }

        binding.btnPlayerClose.setOnClickListener {
            stopAudio()
        }

        binding.sbSurahProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null && mediaPlayer?.duration ?: 0 > 0) {
                    val newPos = (progress / 100f * mediaPlayer!!.duration).toInt()
                    mediaPlayer?.seekTo(newPos)
                    binding.tvPlayerCurrentTime.text = formatTime(newPos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun togglePlay(surah: Surah) {
        if (currentPlayingSurah?.id == surah.id && (mediaPlayer != null || isTtsMode)) {
            if (isAudioActive) {
                pauseAudio()
            } else {
                resumeAudio()
            }
            return
        }
        playSurah(surah)
    }

    private fun playSurah(surah: Surah) {
        stopAudio()
        currentPlayingSurah = surah

        binding.cardFullAudioPlayer.visibility = View.VISIBLE
        binding.tvPlayerSurahTitle.text = "সুরা ${surah.id}: ${surah.nameBangla} (${surah.nameArabic})"
        binding.tvPlayerReciterName.text = currentReciter.nameBn
        binding.sbSurahProgress.progress = 0
        binding.tvPlayerCurrentTime.text = "০০:০০"
        binding.tvPlayerTotalDuration.text = "লোড হচ্ছে..."

        val localFile = File(File(filesDir, "quran_audio"), "surah_${surah.id}.mp3")
        if (localFile.exists() && localFile.length() > 1024) {
            // Play local cached file
            binding.tvPlayerReciterName.text = "${currentReciter.nameBn} (অফলাইনে সংরক্ষিত ✅)"
            startMediaPlayer(surah, localFile.absolutePath, isLocal = true)
        } else {
            // Stream online
            val audioUrl = currentReciter.getSurahAudioUrl(surah.id)
            startMediaPlayer(surah, audioUrl, isLocal = false)
        }
    }

    private fun startMediaPlayer(surah: Surah, source: String, isLocal: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(source)
                setOnPreparedListener { mp ->
                    mp.start()
                    this@AudioQuranActivity.isAudioActive = true
                    isTtsMode = false
                    binding.btnPlayerPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    surahAdapter.setCurrentlyPlayingId(surah.id, true)
                    binding.tvPlayerTotalDuration.text = formatTime(mp.duration)
                    handler.post(progressRunnable)
                }
                setOnCompletionListener {
                    if (isRepeatOne) {
                        playSurah(surah)
                    } else {
                        playNextSurah()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (!isLocal) {
                        val fallback = currentReciter.getSurahFallbackUrl(surah.id)
                        if (fallback != source) {
                            startMediaPlayer(surah, fallback, isLocal = false)
                        } else {
                            playTtsRecitation(surah)
                        }
                    } else {
                        playTtsRecitation(surah)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playTtsRecitation(surah)
        }
    }

    private fun playTtsRecitation(surah: Surah) {
        isTtsMode = true
        isAudioActive = true
        binding.tvPlayerReciterName.text = "অফলাইন অডিও তিলাওয়াত (ইন্টারনেট ছাড়া প্রস্তুত ✅)"
        binding.btnPlayerPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        binding.tvPlayerTotalDuration.text = "অফলাইন মোড"
        surahAdapter.setCurrentlyPlayingId(surah.id, true)

        val text = "سورة ${surah.nameArabic}. بسم الله الرحمن الرحيم"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "QURAN_TTS_${surah.id}")
    }

    private fun pauseAudio() {
        if (isTtsMode) {
            tts?.stop()
        } else {
            mediaPlayer?.pause()
        }
        isAudioActive = false
        binding.btnPlayerPlayPause.setImageResource(android.R.drawable.ic_media_play)
        surahAdapter.setCurrentlyPlayingId(currentPlayingSurah?.id, false)
        handler.removeCallbacks(progressRunnable)
    }

    private fun resumeAudio() {
        if (isTtsMode) {
            currentPlayingSurah?.let { playTtsRecitation(it) }
        } else {
            mediaPlayer?.start()
            isAudioActive = true
            binding.btnPlayerPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            surahAdapter.setCurrentlyPlayingId(currentPlayingSurah?.id, true)
            handler.post(progressRunnable)
        }
    }

    private fun playNextSurah() {
        val currentId = currentPlayingSurah?.id ?: 0
        if (currentId < 114) {
            val nextSurah = allSurahs.find { it.id == currentId + 1 }
            nextSurah?.let { playSurah(it) }
        } else {
            val firstSurah = allSurahs.first()
            playSurah(firstSurah)
        }
    }

    private fun playPreviousSurah() {
        val currentId = currentPlayingSurah?.id ?: 2
        if (currentId > 1) {
            val prevSurah = allSurahs.find { it.id == currentId - 1 }
            prevSurah?.let { playSurah(it) }
        } else {
            val lastSurah = allSurahs.last()
            playSurah(lastSurah)
        }
    }

    private fun stopAudio() {
        handler.removeCallbacks(progressRunnable)
        if (isTtsMode) {
            tts?.stop()
            isTtsMode = false
        }
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
        isAudioActive = false
        currentPlayingSurah = null
        binding.cardFullAudioPlayer.visibility = View.GONE
        surahAdapter.setCurrentlyPlayingId(null, false)
    }

    private fun downloadSurahOffline(surah: Surah) {
        Toast.makeText(this, "সুরা ${surah.nameBangla} অফলাইনে ডাউনলোড শুরু হয়েছে...", Toast.LENGTH_SHORT).show()
        val dir = File(filesDir, "quran_audio")
        if (!dir.exists()) dir.mkdirs()
        val destFile = File(dir, "surah_${surah.id}.mp3")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(currentReciter.getSurahAudioUrl(surah.id))
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.connect()

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val input = conn.inputStream
                    val output = FileOutputStream(destFile)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                    output.close()
                    input.close()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AudioQuranActivity, "সুরা ${surah.nameBangla} অফলাইনে সংরক্ষিত হয়েছে ✅", Toast.LENGTH_SHORT).show()
                        surahAdapter.notifyDataSetChanged()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AudioQuranActivity, "ডাউনলোড ব্যর্থ হয়েছে। অনুগ্রহ করে ইন্টারনেট সংযোগ চেক করুন।", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AudioQuranActivity, "ডাউনলোড সম্পন্ন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                var result = tts?.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("ar", "SA"))
                }
            }
        }
    }

    private fun formatTime(millis: Int): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    override fun onPause() {
        super.onPause()
        // Allow audio to keep playing if user switches tabs/reads Quran, unless destroyed
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
        tts?.stop()
        tts?.shutdown()
    }
}
