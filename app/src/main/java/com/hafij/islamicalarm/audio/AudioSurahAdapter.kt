package com.hafij.islamicalarm.audio

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ItemAudioSurahBinding
import com.hafij.islamicalarm.quran.Surah
import java.io.File

class AudioSurahAdapter(
    private var surahs: List<Surah>,
    private val onPlayClick: (Surah) -> Unit,
    private val onDownloadClick: (Surah) -> Unit,
    private val onReadClick: (Surah) -> Unit
) : RecyclerView.Adapter<AudioSurahAdapter.SurahAudioViewHolder>() {

    private var currentlyPlayingSurahId: Int? = null
    private var isPlaying: Boolean = false

    fun updateList(newList: List<Surah>) {
        surahs = newList
        notifyDataSetChanged()
    }

    fun setCurrentlyPlayingId(surahId: Int?, playing: Boolean) {
        currentlyPlayingSurahId = surahId
        isPlaying = playing
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahAudioViewHolder {
        val binding = ItemAudioSurahBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SurahAudioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SurahAudioViewHolder, position: Int) {
        holder.bind(surahs[position])
    }

    override fun getItemCount(): Int = surahs.size

    inner class SurahAudioViewHolder(private val binding: ItemAudioSurahBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(surah: Surah) {
            val context = binding.root.context

            binding.tvAudioSurahNo.text = toBengaliNumber(surah.id)
            binding.tvAudioSurahNameBn.text = surah.nameBangla
            binding.tvAudioSurahNameAr.text = surah.nameArabic
            binding.tvAudioSurahMeta.text = "${toBengaliNumber(surah.totalAyahs)} আয়াত • ${surah.revelationType} • পারা ${toBengaliNumber(surah.paraNumber)}"

            // Check offline cache
            val localAudio = File(File(context.filesDir, "quran_audio"), "surah_${surah.id}.mp3")
            val isDownloaded = localAudio.exists() && localAudio.length() > 1024

            if (isDownloaded) {
                binding.tvDownloadStatus.text = "অফলাইনে সংরক্ষিত ✅"
                binding.tvDownloadStatus.setTextColor(context.resources.getColor(R.color.secondary, context.theme))
                binding.btnDownloadSurah.setImageResource(android.R.drawable.checkbox_on_background)
                binding.btnDownloadSurah.setColorFilter(context.resources.getColor(R.color.secondary, context.theme))
            } else {
                binding.tvDownloadStatus.text = "অনলাইন স্ট্রিমিং"
                binding.tvDownloadStatus.setTextColor(context.resources.getColor(R.color.text_secondary, context.theme))
                binding.btnDownloadSurah.setImageResource(android.R.drawable.stat_sys_download)
                binding.btnDownloadSurah.setColorFilter(context.resources.getColor(R.color.text_secondary, context.theme))
            }

            val isThisPlaying = (surah.id == currentlyPlayingSurahId && isPlaying)
            if (isThisPlaying) {
                binding.btnPlayAudioSurah.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                binding.btnPlayAudioSurah.setImageResource(android.R.drawable.ic_media_play)
            }

            binding.btnPlayAudioSurah.setOnClickListener {
                onPlayClick(surah)
            }

            binding.root.setOnClickListener {
                onPlayClick(surah)
            }

            binding.btnDownloadSurah.setOnClickListener {
                onDownloadClick(surah)
            }

            binding.btnReadSurahMushaf.setOnClickListener {
                onReadClick(surah)
            }
        }
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
}
