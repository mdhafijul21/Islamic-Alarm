package com.hafij.islamicalarm.audio

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
    private var isRepeatOne = false
    private var isUserSeeking = false
    private var batchDownloadJob: Job? = null
    private var lastPromptedMissingSurahId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioQuranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarAudioQuran.setNavigationOnClickListener { finish() }

        setupReciterChips()
        setupRecyclerView()
        setupSearch()
        setupPlayerControls()
        setupBatchOfflineDownload()
        observeAudioServiceState()
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

                    val serviceState = QuranAudioService.stateFlow.value
                    if (serviceState.surah != null) {
                        QuranAudioService.dispatchAction(
                            context = this,
                            action = QuranAudioService.ACTION_CHANGE_RECITER,
                            promoteForeground = serviceState.isPlaying || serviceState.isBuffering
                        ) {
                            putExtra(QuranAudioService.EXTRA_RECITER_ID, reciter.id)
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
                val ok = downloadFileWithMirrors(reciter.getAllPlaybackUrls(surah.id), dest)
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
            if (currentPlayingSurah != null) {
                val st = QuranAudioService.stateFlow.value
                QuranAudioService.dispatchAction(
                    context = this,
                    action = QuranAudioService.ACTION_TOGGLE_PLAY_PAUSE,
                    promoteForeground = !st.isPlaying
                )
            }
        }

        binding.btnPlayerNext.setOnClickListener {
            QuranAudioService.dispatchAction(
                context = this,
                action = QuranAudioService.ACTION_NEXT,
                promoteForeground = true
            )
        }

        binding.btnPlayerPrev.setOnClickListener {
            QuranAudioService.dispatchAction(
                context = this,
                action = QuranAudioService.ACTION_PREV,
                promoteForeground = true
            )
        }

        binding.btnPlayerRepeat.setOnClickListener {
            isRepeatOne = !isRepeatOne
            QuranAudioService.dispatchAction(this, QuranAudioService.ACTION_SET_REPEAT) {
                putExtra(QuranAudioService.EXTRA_REPEAT_ONE, isRepeatOne)
            }
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

        binding.btnPlayerClose.setOnClickListener {
            QuranAudioService.dispatchAction(this, QuranAudioService.ACTION_STOP)
        }

        binding.sbSurahProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val st = QuranAudioService.stateFlow.value
                if (fromUser && !st.isTtsMode && st.durationMs > 0) {
                    val newPos = (progress / 100f * st.durationMs).toInt()
                    binding.tvPlayerCurrentTime.text = formatTime(newPos)
                    QuranAudioService.dispatchAction(this@AudioQuranActivity, QuranAudioService.ACTION_SEEK) {
                        putExtra(QuranAudioService.EXTRA_SEEK_POS_MS, newPos)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
            }
        })
    }

    private fun observeAudioServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                QuranAudioService.stateFlow.collect { state ->
                    currentPlayingSurah = state.surah
                    isRepeatOne = state.isRepeatOne

                    val surah = state.surah
                    if (surah == null) {
                        binding.cardFullAudioPlayer.visibility = View.GONE
                        surahAdapter.setCurrentlyPlayingId(null, false)
                        return@collect
                    }

                    binding.cardFullAudioPlayer.visibility = View.VISIBLE
                    binding.tvPlayerSurahTitle.text =
                        "সুরা ${surah.id}: ${surah.nameBangla} (${surah.nameArabic})"
                    binding.tvPlayerReciterName.text = state.statusText.ifBlank { state.reciter.nameBn }

                    val isActive = state.isPlaying || state.isBuffering
                    binding.btnPlayerPlayPause.setImageResource(
                        if (isActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    )
                    surahAdapter.setCurrentlyPlayingId(surah.id, isActive)

                    if (state.isBuffering) {
                        binding.tvPlayerCurrentTime.text = "০০:০০"
                        binding.tvPlayerTotalDuration.text = "লোড হচ্ছে..."
                        if (!isUserSeeking) binding.sbSurahProgress.progress = 0
                    } else if (state.isTtsMode) {
                        val total = state.ttsTotalAyahs.coerceAtLeast(1)
                        val idx = state.ttsAyahIndex
                        val pct = ((idx + 1) * 100 / total).coerceIn(1, 100)
                        if (!isUserSeeking) binding.sbSurahProgress.progress = pct
                        binding.tvPlayerCurrentTime.text = "আয়াত ${idx + 1}"
                        binding.tvPlayerTotalDuration.text = "মোট $total"
                    } else if (state.durationMs > 0) {
                        val progress = (state.positionMs.toFloat() / state.durationMs * 100).toInt().coerceIn(0, 100)
                        if (!isUserSeeking) binding.sbSurahProgress.progress = progress
                        binding.tvPlayerCurrentTime.text = formatTime(state.positionMs)
                        binding.tvPlayerTotalDuration.text = formatTime(state.durationMs)
                    }

                    val missingId = state.missingOfflinePromptSurahId
                    if (missingId != null && missingId != lastPromptedMissingSurahId) {
                        lastPromptedMissingSurahId = missingId
                        allSurahs.find { it.id == missingId }?.let { showMissingOfflineAudioDialog(it) }
                        QuranAudioService.dispatchAction(
                            this@AudioQuranActivity,
                            QuranAudioService.ACTION_CLEAR_MISSING_PROMPT
                        )
                    } else if (missingId == null) {
                        lastPromptedMissingSurahId = null
                    }
                }
            }
        }
    }

    private fun togglePlay(surah: Surah) {
        val current = QuranAudioService.stateFlow.value
        if (current.surah?.id == surah.id) {
            QuranAudioService.dispatchAction(
                context = this,
                action = QuranAudioService.ACTION_TOGGLE_PLAY_PAUSE,
                promoteForeground = !current.isPlaying
            )
            return
        }
        lastPromptedMissingSurahId = null
        QuranAudioService.dispatchAction(
            context = this,
            action = QuranAudioService.ACTION_PLAY_SURAH,
            promoteForeground = true
        ) {
            putExtra(QuranAudioService.EXTRA_SURAH_ID, surah.id)
            putExtra(QuranAudioService.EXTRA_RECITER_ID, currentReciter.id)
        }
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
            val ok = downloadFileWithMirrors(reciter.getAllPlaybackUrls(surah.id), destFile)
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

    private fun downloadFileWithMirrors(urls: List<String>, destFile: File): Boolean {
        for (url in urls) {
            if (url.isNotBlank() && downloadSingleUrl(url, destFile)) return true
        }
        return false
    }

    private fun downloadSingleUrl(urlStr: String, destFile: File): Boolean {
        val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.connect()
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(16384)
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

    private fun formatTime(millis: Int): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        batchDownloadJob?.cancel()
        // Intentionally do NOT stop QuranAudioService here so recitation continues playing in background.
        super.onDestroy()
    }
}
