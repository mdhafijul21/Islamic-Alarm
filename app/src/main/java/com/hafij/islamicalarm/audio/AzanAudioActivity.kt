package com.hafij.islamicalarm.audio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.databinding.ActivityAzanAudioBinding
import java.util.Locale

class AzanAudioActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityAzanAudioBinding
    private lateinit var azanAdapter: AzanAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var playingAzanId: String? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAzanAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarAzan.setNavigationOnClickListener {
            finish()
        }

        setupSwitchAndCurrentStatus()
        setupAzanList()
        setupPhrasesAndDua()

        tts = TextToSpeech(this, this)
    }

    private fun setupSwitchAndCurrentStatus() {
        val prefs = getSharedPreferences("IslamicAlarmPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(AzanData.PREF_AZAN_SOUND_ENABLED, true)
        val selectedId = prefs.getString(AzanData.PREF_SELECTED_AZAN_ID, "makkah_ali_mullah") ?: "makkah_ali_mullah"

        binding.switchAzanAlarm.isChecked = isEnabled
        updateCurrentSelectedText(selectedId)

        binding.switchAzanAlarm.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(AzanData.PREF_AZAN_SOUND_ENABLED, isChecked).apply()
            val msg = if (isChecked) "সালাতের এলার্মে আজান চালু করা হয়েছে" else "সালাতের এলার্মে সাধারণ অ্যালার্ম রিংটোন বাজবে"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCurrentSelectedText(selectedId: String) {
        val azan = AzanData.azanList.find { it.id == selectedId } ?: AzanData.azanList.first()
        binding.tvCurrentSelectedAzan.text = "বর্তমান নির্বাচিত আজান: ${azan.titleBn}"
    }

    private fun setupAzanList() {
        val prefs = getSharedPreferences("IslamicAlarmPrefs", Context.MODE_PRIVATE)
        val selectedId = prefs.getString(AzanData.PREF_SELECTED_AZAN_ID, "makkah_ali_mullah") ?: "makkah_ali_mullah"

        azanAdapter = AzanAdapter(
            azanList = AzanData.azanList,
            selectedAzanId = selectedId,
            onPlayClick = { item ->
                togglePlayAzan(item)
            },
            onSetAlarmClick = { item ->
                prefs.edit().putString(AzanData.PREF_SELECTED_AZAN_ID, item.id).apply()
                azanAdapter.setSelectedAzanId(item.id)
                updateCurrentSelectedText(item.id)
                Toast.makeText(this, "${item.titleBn} সফলভাবে এলার্ম টিউন হিসেবে সেট করা হয়েছে ✅", Toast.LENGTH_LONG).show()
            }
        )

        binding.rvAzanList.layoutManager = LinearLayoutManager(this)
        binding.rvAzanList.adapter = azanAdapter
    }

    private fun togglePlayAzan(item: AzanItem) {
        if (playingAzanId == item.id) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                azanAdapter.setPlaybackState(item.id, false)
            } else {
                mediaPlayer?.start()
                azanAdapter.setPlaybackState(item.id, true)
            }
            return
        }

        stopAudio()
        playingAzanId = item.id
        azanAdapter.setPlaybackState(item.id, true)
        Toast.makeText(this, "${item.titleBn} আজান লোড হচ্ছে...", Toast.LENGTH_SHORT).show()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(item.audioUrl)
                setOnPreparedListener { mp ->
                    mp.start()
                    azanAdapter.setPlaybackState(item.id, true)
                }
                setOnCompletionListener {
                    azanAdapter.setPlaybackState(null, false)
                    playingAzanId = null
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@AzanAudioActivity, "আজান অডিও লোড করতে ব্যর্থ হয়েছে। ইন্টারনেট সংযোগ চেক করুন।", Toast.LENGTH_SHORT).show()
                    azanAdapter.setPlaybackState(null, false)
                    playingAzanId = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "আজান চালাতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
            azanAdapter.setPlaybackState(null, false)
            playingAzanId = null
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
        playingAzanId = null
        azanAdapter.setPlaybackState(null, false)
    }

    private fun setupPhrasesAndDua() {
        val sbPhrases = java.lang.StringBuilder()
        AzanData.azanPhrases.forEachIndexed { index, pair ->
            sbPhrases.append("${index + 1}. ${pair.first}\n   অর্থ ও উত্তর: ${pair.second}\n\n")
        }
        binding.tvAzanPhrasesText.text = sbPhrases.toString().trim()

        binding.tvAzanDuaArabic.text = AzanData.AZAN_DUA_ARABIC
        binding.tvAzanDuaPronunciation.text = AzanData.AZAN_DUA_BANGLA_PRONUNCIATION
        binding.tvAzanDuaMeaning.text = AzanData.AZAN_DUA_BANGLA_MEANING
        binding.tvAzanDuaFazilat.text = AzanData.AZAN_DUA_FAZILAT

        binding.btnCopyAzanDua.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = "🤲 আজান শেষের মাসনুন দোয়া:\n\n${AzanData.AZAN_DUA_ARABIC}\n\nউচ্চারণ: ${AzanData.AZAN_DUA_BANGLA_PRONUNCIATION}\n\nঅর্থ: ${AzanData.AZAN_DUA_BANGLA_MEANING}\n\nফজিলত: ${AzanData.AZAN_DUA_FAZILAT}"
            clipboard.setPrimaryClip(ClipData.newPlainText("Azan Dua", textToCopy))
            Toast.makeText(this, "আজানের দোয়া কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
        }

        binding.btnPlayDuaTts.setOnClickListener {
            val textToSpeak = "${AzanData.AZAN_DUA_BANGLA_PRONUNCIATION}। ${AzanData.AZAN_DUA_BANGLA_MEANING}"
            if (isTtsReady && tts != null) {
                tts?.stop()
                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "AZAN_DUA_TTS")
                Toast.makeText(this, "আজানের দোয়া অডিও পাঠ হচ্ছে...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ভয়েস রিডার প্রস্তুত হচ্ছে...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("bn", "BD"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("bn"))
            }
            isTtsReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        stopAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
        tts?.stop()
        tts?.shutdown()
    }
}
