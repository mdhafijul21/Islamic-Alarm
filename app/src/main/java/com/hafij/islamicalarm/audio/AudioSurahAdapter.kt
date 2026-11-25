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
    private val getReciterId: () -> String = { "mishary" },
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
            binding.tvAudioSurahNameBn.text = "${toBengaliNumber(surah.id)}. ${surah.nameBangla}"
            binding.tvAudioSurahNameAr.text = surah.nameArabic
            binding.tvAudioSurahMeta.text =
                "মোট ${toBengaliNumber(surah.totalAyahs)} আয়াত • ${surah.revelationType} • পারা ${toBengaliNumber(surah.paraNumber)}"
            binding.tvAudioSurahMeaning.text = "অর্থ: ${surah.meaningBangla}"

            val isDownloaded = resolveLocalAudioFile(context, getReciterId(), surah.id) != null
            val secondaryColor = context.resources.getColor(R.color.secondary, context.theme)
            val surfaceContainerColor = context.resources.getColor(R.color.surface_container, context.theme)
            val textPrimaryColor = context.resources.getColor(R.color.text_primary, context.theme)
            val textSecondaryColor = context.resources.getColor(R.color.text_secondary, context.theme)
            val goldAccentColor = context.resources.getColor(R.color.gold_accent, context.theme)

            if (isDownloaded) {
                binding.tvDownloadStatus.text = "অফলাইন ✅"
                binding.tvDownloadStatus.setTextColor(secondaryColor)
                binding.btnDownloadSurah.text = "✅ সেভড"
                binding.btnDownloadSurah.setTextColor(secondaryColor)
                binding.btnDownloadSurah.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(surfaceContainerColor)
            } else {
                binding.tvDownloadStatus.text = "অনলাইন"
                binding.tvDownloadStatus.setTextColor(textSecondaryColor)
                binding.btnDownloadSurah.text = "⬇️ সেভ"
                binding.btnDownloadSurah.setTextColor(textPrimaryColor)
                binding.btnDownloadSurah.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(surfaceContainerColor)
            }

            val isThisPlaying = (surah.id == currentlyPlayingSurahId && isPlaying)
            if (isThisPlaying) {
                binding.btnPlayAudioSurah.text = "⏸ থামান"
                binding.btnPlayAudioSurah.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(goldAccentColor)
            } else {
                binding.btnPlayAudioSurah.text = "▶ শুনুন"
                binding.btnPlayAudioSurah.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(secondaryColor)
            }

            binding.btnPlayAudioSurah.setOnClickListener { onPlayClick(surah) }
            binding.root.setOnClickListener { onPlayClick(surah) }
            binding.btnDownloadSurah.setOnClickListener { onDownloadClick(surah) }
            binding.btnReadSurahMushaf.setOnClickListener { onReadClick(surah) }
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

    companion object {
        fun targetAudioFile(context: Context, reciterId: String, surahId: Int): File {
            val dir = File(context.filesDir, "quran_audio")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "${reciterId}_surah_${surahId}.mp3")
        }

        fun resolveLocalAudioFile(context: Context, reciterId: String, surahId: Int): File? {
            val dir = File(context.filesDir, "quran_audio")
            val specific = File(dir, "${reciterId}_surah_${surahId}.mp3")
            if (specific.exists() && specific.length() > 1024) return specific
            if (reciterId == "mishary") {
                val legacy = File(dir, "surah_${surahId}.mp3")
                if (legacy.exists() && legacy.length() > 1024) return legacy
            }
            return null
        }

        fun countDownloadedForReciter(context: Context, reciterId: String): Int {
            var count = 0
            for (id in 1..114) {
                if (resolveLocalAudioFile(context, reciterId, id) != null) count++
            }
            return count
        }
    }
}
