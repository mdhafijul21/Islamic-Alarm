package com.hafij.islamicalarm.quran

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemSurahBinding

class SurahAdapter(
    private var surahList: List<Surah>,
    private val onReadPage: (Surah) -> Unit
) : RecyclerView.Adapter<SurahAdapter.SurahViewHolder>() {

    fun updateData(newList: List<Surah>) {
        surahList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurahViewHolder {
        val binding = ItemSurahBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SurahViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SurahViewHolder, position: Int) {
        holder.bind(surahList[position])
    }

    override fun getItemCount(): Int = surahList.size

    inner class SurahViewHolder(private val binding: ItemSurahBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(surah: Surah) {
            binding.tvSurahNumber.text = toBengaliNumber(surah.id)
            binding.tvSurahBangla.text = "সুরা ${surah.nameBangla}"
            binding.tvSurahArabic.text = surah.nameArabic
            binding.tvSurahMeaning.text = "অর্থ: ${surah.meaningBangla} • আয়াত: ${toBengaliNumber(surah.totalAyahs)}"
            binding.tvRevelationType.text = surah.revelationType
            binding.tvParaNumber.text = "পারা ${toBengaliNumber(surah.paraNumber)}"

            binding.btnReadPage.setOnClickListener {
                onReadPage(surah)
            }

            binding.root.setOnClickListener {
                onReadPage(surah)
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
}
