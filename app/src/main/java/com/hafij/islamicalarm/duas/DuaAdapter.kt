package com.hafij.islamicalarm.duas

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ItemDuaBinding

class DuaAdapter : RecyclerView.Adapter<DuaAdapter.DuaViewHolder>() {
    private var items: List<DuaItem> = emptyList()

    fun submitList(newList: List<DuaItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DuaViewHolder {
        val binding = ItemDuaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DuaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DuaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DuaViewHolder(private val binding: ItemDuaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dua: DuaItem) {
            binding.tvDuaTitle.text = dua.title
            binding.tvDuaCategoryTag.text = dua.category
            binding.ivDuaIllustration.setImageResource(imageForCategory(dua.category))
            binding.ivDuaIllustration.contentDescription = dua.category

            if (dua.arabic.isBlank()) {
                binding.tvDuaArabic.visibility = android.view.View.GONE
            } else {
                binding.tvDuaArabic.visibility = android.view.View.VISIBLE
                binding.tvDuaArabic.text = dua.arabic
            }

            if (dua.translation.isBlank()) {
                binding.tvDuaTranslation.visibility = android.view.View.GONE
            } else {
                binding.tvDuaTranslation.visibility = android.view.View.VISIBLE
                binding.tvDuaTranslation.text = "অর্থ: ${dua.translation}"
            }

            binding.tvDuaWhen.visibility =
                if (dua.whenToRead.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.tvDuaWhen.text =
                if (dua.whenToRead.isBlank()) "" else "সময়: ${dua.whenToRead}"

            val openDetail = {
                val context = binding.root.context
                val intent = Intent(context, DuaDetailActivity::class.java).apply {
                    putExtra(DuaDetailActivity.EXTRA_ID, dua.id)
                    putExtra(DuaDetailActivity.EXTRA_TITLE, dua.title)
                    putExtra(DuaDetailActivity.EXTRA_CATEGORY, dua.category)
                    putExtra(DuaDetailActivity.EXTRA_ARABIC, dua.arabic)
                    putExtra(DuaDetailActivity.EXTRA_TRANSLITERATION, dua.transliteration)
                    putExtra(DuaDetailActivity.EXTRA_TRANSLATION, dua.translation)
                    putExtra(DuaDetailActivity.EXTRA_REFERENCE, dua.reference)
                    putExtra(DuaDetailActivity.EXTRA_WHEN, dua.whenToRead)
                    putExtra(DuaDetailActivity.EXTRA_COUNT, dua.repeatCount)
                    putExtra(DuaDetailActivity.EXTRA_VIRTUE, dua.virtue)
                    putExtra(DuaDetailActivity.EXTRA_GRADE, dua.grade)
                    putExtra(DuaDetailActivity.EXTRA_TYPE, dua.contentType)
                    putExtra(DuaDetailActivity.EXTRA_URL, dua.sourceUrl)
                }
                context.startActivity(intent)
            }
            binding.btnOpenDuaDetail.setOnClickListener { openDetail() }
            binding.root.setOnClickListener { openDetail() }
        }

        private fun imageForCategory(category: String): Int = when (category) {
            DuaData.CATEGORY_MORNING_EVENING, DuaData.CATEGORY_AFTER_FARD,
            DuaData.CATEGORY_MOSQUE, DuaData.CATEGORY_FRIDAY -> R.drawable.dua_cat_mosque
            DuaData.CATEGORY_RABBANA, DuaData.CATEGORY_PROPHETS,
            DuaData.CATEGORY_TAWBAH, DuaData.CATEGORY_HADITH -> R.drawable.dua_cat_quran
            DuaData.CATEGORY_SLEEP, DuaData.CATEGORY_NAFL,
            DuaData.CATEGORY_TAHAJJUD, DuaData.CATEGORY_ISTIKHARA,
            DuaData.CATEGORY_IN_PRAYER -> R.drawable.dua_cat_prayer
            DuaData.CATEGORY_FASTING, DuaData.CATEGORY_RAMADAN,
            DuaData.CATEGORY_HAJJ, DuaData.CATEGORY_CHARITY -> R.drawable.dua_cat_ramadan
            else -> R.drawable.dua_cat_mosque
        }
    }
}
