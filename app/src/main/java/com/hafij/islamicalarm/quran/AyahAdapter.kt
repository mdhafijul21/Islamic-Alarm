package com.hafij.islamicalarm.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemAyahBinding

class AyahAdapter(
    private var ayahs: List<AyahItem>,
    private val surahNameBn: String
) : RecyclerView.Adapter<AyahAdapter.AyahViewHolder>() {

    var fontScaleDelta: Float = 0f

    fun updateList(newList: List<AyahItem>) {
        ayahs = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AyahViewHolder {
        val binding = ItemAyahBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AyahViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AyahViewHolder, position: Int) {
        holder.bind(ayahs[position])
    }

    override fun getItemCount(): Int = ayahs.size

    inner class AyahViewHolder(private val binding: ItemAyahBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ayah: AyahItem) {
            val context = binding.root.context

            binding.tvAyahNumberBadge.text = "আয়াত ${toBengaliNumber(ayah.numberInSurah)}"

            // Dynamic font sizing
            val baseArabicSize = 22f + fontScaleDelta
            val baseBanglaSize = 15f + fontScaleDelta

            binding.tvAyahArabic.textSize = baseArabicSize
            binding.tvAyahBangla.textSize = baseBanglaSize

            binding.tvAyahArabic.text = ayah.textArabic
            binding.tvAyahBangla.text = ayah.textBangla

            // Copy Ayah
            binding.btnCopyAyah.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val textToCopy = buildString {
                    append(ayah.textArabic).append("\n\n")
                    append(ayah.textBangla).append("\n\n")
                    append("— [সুরা ").append(surahNameBn).append(": আয়াত ").append(ayah.numberInSurah).append("]")
                }
                val clip = ClipData.newPlainText("Quran Ayah", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "আয়াত কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
            }

            // Share Ayah
            binding.btnShareAyah.setOnClickListener {
                val shareText = buildString {
                    append(ayah.textArabic).append("\n\n")
                    append(ayah.textBangla).append("\n\n")
                    append("— [সুরা ").append(surahNameBn).append(": আয়াত ").append(ayah.numberInSurah).append("]\n")
                    append("আল-কুরআন ও ইসলামিক এলার্ম অ্যাপ")
                }
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "আয়াত শেয়ার করুন"))
            }
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
}
