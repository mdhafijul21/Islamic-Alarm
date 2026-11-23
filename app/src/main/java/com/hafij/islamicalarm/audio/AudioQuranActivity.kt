package com.hafij.islamicalarm.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityAudioQuranBinding
import com.hafij.islamicalarm.quran.QuranRepository
import com.hafij.islamicalarm.quran.Surah
import com.hafij.islamicalarm.quran.SurahDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    private var ttsReady = false
    private var ttsArabicAvailable = false
    private var isTtsMode = false
    private var ttsAyahs: List<String> = emptyList()
    private var ttsAyahIndex = 0
    private var batchDownloadJob: Job? = null

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

        binding.toolbarAudioQuran.setNavigationOnClickListener { finish() }

        initTts()
        setupReciterChips()
        setupRecyclerView()
        setupSearch()
        setupPlayerControls()
        setupBatchOfflineDownload()
        refreshCountLabel()
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
                    surahAdapter.notifyDataSetChanged()
                    refreshCountLabel()
                    Toast.makeText(this, "${reciter.nameBn} নির্বাচন করা হয়েছে", Toast.LENGTH_SHORT).show()

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
            getReciterId = { currentReciter.id },
            onPlayClick = { surah -> togglePlay(surah) },
            onDownloadClick = { surah -> downloadSurahOffline(surah) },
            onReadClick = { surah ->
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra("EXTRA_SURAH_ID", surah.id)
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
        refreshCountLabel()
    }

    private fun refreshCountLabel() {
        val downloaded = AudioSurahAdapter.countDownloadedForReciter(this, currentReciter.id)
        binding.tvSurahCountLabel.text =
            "📖 ${filteredSurahs.size}টি সুরা • অফলাইনে সংরক্ষিত: $downloaded/১১৪"
    }

    private fun setupBatchOfflineDownload() {
        binding.btnDownloadAllOffline.setOnClickListener {
            if (batchDownloadJob?.isActive == true) {
                batchDownloadJob?.cancel()
                batchDownloadJob = null
                binding.btnDownloadAllOffline.text = "⬇️ সব অফলাইন সেভ"
                binding.pbBatchDownload.visibility = View.GONE
                Toast.makeText(this, "ডাউনলোড থামানো হয়েছে", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!hasInternetConnection()) {
                Toast.makeText(
                    this,
                    "ক্বারীর MP3 ডাউনলোড করতে একবার ইন্টারনেট সংযোগ প্রয়োজন।",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val reciter = currentReciter
            val remaining = allSurahs.filter {
                AudioSurahAdapter.resolveLocalAudioFile(this, reciter.id, it.id) == null
            }
            if (remaining.isEmpty()) {
                Toast.makeText(this, "এই ক্বারীর সব ১১৪টি সুরা ইতিমধ্যে অফলাইনে সংরক্ষিত ✅", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("⬇️ সব ১১৪ সুরা অফলাইন সেভ")
                .setMessage(
                    "${reciter.nameBn}-এর বাকি ${remaining.size}টি সুরা অফলাইন ব্যবহারের জন্য ফোনে ডাউনলোড হবে।\n" +
                        "একবার সেভ হলে আর কখনো ইন্টারনেট ছাড়াই ক্বারীর কণ্ঠে বাজবে (প্রায় ৪০০–৭০০ MB জায়গা লাগতে পারে)।"
                )
                .setPositiveButton("শুরু করুন") { _, _ -> startBatchDownload(reciter, remaining) }
                .setNegativeButton("বাতিল", null)
                .show()
        }
    }

    private fun startBatchDownload(reciter: Reciter, surahsToDownload: List<Surah>) {
        binding.btnDownloadAllOffline.text = "⏹️ থামান"
        binding.pbBatchDownload.visibility = View.VISIBLE
        binding.pbBatchDownload.max = surahsToDownload.size
        binding.pbBatchDownload.progress = 0

        batchDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            var completed = 0
            for (surah in surahsToDownload) {
                if (!isActive) break
                val dest = AudioSurahAdapter.targetAudioFile(this@AudioQuranActivity, reciter.id, surah.id)
                val ok = downloadFileWithFallback(
                    primaryUrl = reciter.getSurahAudioUrl(surah.id),
                    fallbackUrl = reciter.getSurahFallbackUrl(surah.id),
                    destFile = dest
                )
                completed++
                val c = completed
                withContext(Dispatchers.Main) {
                    binding.pbBatchDownload.progress = c
                    binding.tvSurahCountLabel.text =
                        "⬇️ সেভ হচ্ছে: ${surah.nameBangla} ($c/${surahsToDownload.size})"
                    if (ok) {
                        surahAdapter.notifyDataSetChanged()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                binding.btnDownloadAllOffline.text = "⬇️ সব অফলাইন সেভ"
                binding.pbBatchDownload.visibility = View.GONE
                refreshCountLabel()
                Toast.makeText(
                    this@AudioQuranActivity,
                    "অফলাইন সেভ সম্পন্ন ✅",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupPlayerControls() {
        binding.btnPlayerPlayPause.setOnClickListener {
            currentPlayingSurah?.let {
                if (isAudioActive) pauseAudio() else resumeAudio()
            }
        }

        binding.btnPlayerNext.setOnClickListener { playNextSurah() }
        binding.btnPlayerPrev.setOnClickListener { playPreviousSurah() }

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
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra("EXTRA_SURAH_ID", surah.id)
                }
                startActivity(intent)
            }
        }

        binding.btnPlayerClose.setOnClickListener { stopAudio() }

        binding.sbSurahProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isTtsMode && mediaPlayer != null && (mediaPlayer?.duration ?: 0) > 0) {
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
            if (isAudioActive) pauseAudio() else resumeAudio()
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

        val localFile = AudioSurahAdapter.resolveLocalAudioFile(this, currentReciter.id, surah.id)
        if (localFile != null) {
            binding.tvPlayerReciterName.text = "${currentReciter.nameBn} (অফলাইনে সংরক্ষিত ✅)"
            startMediaPlayer(surah, localFile.absolutePath, isLocal = true)
        } else if (hasInternetConnection()) {
            val audioUrl = currentReciter.getSurahAudioUrl(surah.id)
            startMediaPlayer(surah, audioUrl, isLocal = false)
        } else {
            // Instant offline playback using bundled quran_uthmani.json via Arabic TTS
            playTtsRecitation(surah, resetIndex = true)
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
                    if (isRepeatOne) playSurah(surah) else playNextSurah()
                }
                setOnErrorListener { _, _, _ ->
                    if (!isLocal) {
                        val fallback = currentReciter.getSurahFallbackUrl(surah.id)
                        if (fallback != source) {
                            startMediaPlayer(surah, fallback, isLocal = false)
                        } else {
                            playTtsRecitation(surah, resetIndex = true)
                        }
                    } else {
                        playTtsRecitation(surah, resetIndex = true)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playTtsRecitation(surah, resetIndex = true)
        }
    }

    private fun playTtsRecitation(surah: Surah, resetIndex: Boolean) {
        if (ttsReady && !ttsArabicAvailable) {
            isTtsMode = false
            isAudioActive = false
            binding.btnPlayerPlayPause.setImageResource(android.R.drawable.ic_media_play)
            surahAdapter.setCurrentlyPlayingId(null, false)
            binding.tvPlayerReciterName.text = "অফলাইন আরবি ভয়েস ফোনে পাওয়া যায়নি"
            binding.tvPlayerTotalDuration.text = "MP3 ডাউনলোড বা আরবি ভয়েস সেট করুন"
            showMissingOfflineAudioDialog(surah)
            return
        }

        isTtsMode = true
        isAudioActive = true
        binding.btnPlayerPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        surahAdapter.setCurrentlyPlayingId(surah.id, true)

        if (resetIndex || ttsAyahs.isEmpty()) {
            ttsAyahIndex = 0
            binding.tvPlayerReciterName.text = "অফলাইন আরবি তিলাওয়াত লোড হচ্ছে..."
            lifecycleScope.launch {
                val ayahs = QuranRepository.getOfflineArabicSurahAyahs(this@AudioQuranActivity, surah.id)
                    .map { it.textArabic }
                    .ifEmpty { listOf("سورة ${surah.nameArabic}. بسم الله الرحمن الرحيم") }
                if (!isTtsMode || !isAudioActive || currentPlayingSurah?.id != surah.id) return@launch
                ttsAyahs = ayahs
                speakCurrentTtsAyah(surah)
            }
        } else {
            speakCurrentTtsAyah(surah)
        }
    }

    private fun speakCurrentTtsAyah(surah: Surah) {
        if (!isTtsMode || !isAudioActive || currentPlayingSurah?.id != surah.id) return
        val total = ttsAyahs.size.coerceAtLeast(1)
        if (ttsAyahIndex >= total) {
            if (isRepeatOne) {
                ttsAyahIndex = 0
            } else {
                playNextSurah()
                return
            }
        }

        val idx = ttsAyahIndex
        val ayahText = ttsAyahs[idx]
        val pct = ((idx + 1) * 100 / total).coerceIn(1, 100)
        binding.sbSurahProgress.progress = pct
        binding.tvPlayerCurrentTime.text = "আয়াত ${idx + 1}"
        binding.tvPlayerTotalDuration.text = "মোট $total"
        binding.tvPlayerReciterName.text =
            "অফলাইন আরবি ভয়েস তিলাওয়াত (${idx + 1}/$total)"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (isTtsMode && isAudioActive && currentPlayingSurah?.id == surah.id) {
                    ttsAyahIndex++
                    handler.post { speakCurrentTtsAyah(surah) }
                }
            }
            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (isTtsMode && isAudioActive && currentPlayingSurah?.id == surah.id) {
                    ttsAyahIndex++
                    handler.post { speakCurrentTtsAyah(surah) }
                }
            }
        })
        tts?.speak(ayahText, TextToSpeech.QUEUE_FLUSH, null, "QURAN_TTS_${surah.id}_$idx")
    }

    private fun showMissingOfflineAudioDialog(surah: Surah) {
        MaterialAlertDialogBuilder(this)
            .setTitle("অফলাইন অডিও পাওয়া যায়নি")
            .setMessage(
                "সুরা ${surah.nameBangla}-এর MP3 এখনো ডাউনলোড করা নেই এবং আপনার ফোনে অফলাইন আরবি ভয়েস (Arabic TTS) ইনস্টল নেই।\n\n" +
                    "২টি উপায়ে অফলাইনে শুনতে পারবেন:\n" +
                    "১) একবার ইন্টারনেটে যুক্ত হয়ে ডাউনলোড বাটনে (⬇️) চাপলে ক্বারীর কণ্ঠে সারাজীবন অফলাইনে চলবে।\n" +
                    "২) অথবা ফোনের সেটিংস থেকে Arabic Text-to-Speech ভয়েস ইনস্টল করুন।"
            )
            .setPositiveButton("আরবি ভয়েস সেটিংস") { _, _ ->
                try {
                    startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (_: Exception) {
                    Toast.makeText(this, "ফোনের TTS সেটিংস থেকে Arabic ডাউনলোড করুন", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("ঠিক আছে", null)
            .show()
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
            currentPlayingSurah?.let { playTtsRecitation(it, resetIndex = false) }
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
        val nextId = if (currentId < 114) currentId + 1 else 1
        allSurahs.find { it.id == nextId }?.let { playSurah(it) }
    }

    private fun playPreviousSurah() {
        val currentId = currentPlayingSurah?.id ?: 2
        val prevId = if (currentId > 1) currentId - 1 else 114
        allSurahs.find { it.id == prevId }?.let { playSurah(it) }
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
        ttsAyahs = emptyList()
        ttsAyahIndex = 0
        binding.cardFullAudioPlayer.visibility = View.GONE
        surahAdapter.setCurrentlyPlayingId(null, false)
    }

    private fun downloadSurahOffline(surah: Surah) {
        val reciter = currentReciter
        if (AudioSurahAdapter.resolveLocalAudioFile(this, reciter.id, surah.id) != null) {
            Toast.makeText(this, "সুরা ${surah.nameBangla} ইতিমধ্যে অফলাইনে সংরক্ষিত ✅", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasInternetConnection()) {
            Toast.makeText(this, "ডাউনলোডের জন্য একবার ইন্টারনেট সংযোগ চালু করুন", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "সুরা ${surah.nameBangla} অফলাইনে ডাউনলোড শুরু হয়েছে...", Toast.LENGTH_SHORT).show()
        val destFile = AudioSurahAdapter.targetAudioFile(this, reciter.id, surah.id)

        lifecycleScope.launch(Dispatchers.IO) {
            val ok = downloadFileWithFallback(
                primaryUrl = reciter.getSurahAudioUrl(surah.id),
                fallbackUrl = reciter.getSurahFallbackUrl(surah.id),
                destFile = destFile
            )
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(this@AudioQuranActivity, "সুরা ${surah.nameBangla} অফলাইনে সংরক্ষিত হয়েছে ✅", Toast.LENGTH_SHORT).show()
                    surahAdapter.notifyDataSetChanged()
                    refreshCountLabel()
                } else {
                    Toast.makeText(this@AudioQuranActivity, "ডাউনলোড ব্যর্থ হয়েছে। ইন্টারনেট সংযোগ চেক করুন।", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun downloadFileWithFallback(primaryUrl: String, fallbackUrl: String, destFile: File): Boolean {
        if (downloadSingleUrl(primaryUrl, destFile)) return true
        if (fallbackUrl.isNotBlank() && fallbackUrl != primaryUrl) {
            return downloadSingleUrl(fallbackUrl, destFile)
        }
        return false
    }

    private fun downloadSingleUrl(urlStr: String, destFile: File): Boolean {
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.connect()
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }
                if (tmpFile.length() > 1024) {
                    tmpFile.renameTo(destFile)
                } else {
                    tmpFile.delete()
                    false
                }
            } else {
                tmpFile.delete()
                false
            }
        } catch (e: Exception) {
            tmpFile.delete()
            false
        }
    }

    private fun hasInternetConnection(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = true
            if (status == TextToSpeech.SUCCESS) {
                var result = tts?.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts?.setLanguage(Locale("ar", "SA")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                }
                ttsArabicAvailable = (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED)
            } else {
                ttsArabicAvailable = false
            }
        }
    }

    private fun formatTime(millis: Int): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        batchDownloadJob?.cancel()
        super.onDestroy()
        stopAudio()
        tts?.stop()
        tts?.shutdown()
    }
}
