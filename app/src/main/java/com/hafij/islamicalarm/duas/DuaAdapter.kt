package com.hafij.islamicalarm.duas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ItemDuaBinding

class DuaAdapter(private val context: Context) : RecyclerView.Adapter<DuaAdapter.DuaViewHolder>() {
    private var items: List<DuaItem> = emptyList()

    fun submitList(newList: List<DuaItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DuaViewHolder {
        val binding = ItemDuaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DuaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DuaViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class DuaViewHolder(private val binding: ItemDuaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dua: DuaItem) {
            binding.tvDuaTitle.text = dua.title
            binding.tvDuaCategoryTag.text = dua.category
            binding.ivDuaIllustration.setImageResource(imageForCategory(dua.category))
            binding.ivDuaIllustration.contentDescription = dua.category

            setOptional(binding.tvDuaArabic, dua.arabic)
            setOptional(binding.tvDuaTransliteration, if (dua.transliteration.isBlank()) "" else "উচ্চারণ: ${dua.transliteration}")
            setOptional(binding.tvDuaTranslation, if (dua.translation.isBlank()) "" else "অর্থ / বিস্তারিত: ${dua.translation}")
            setOptional(binding.tvDuaWhen, if (dua.whenToRead.isBlank()) "" else "কখন: ${dua.whenToRead}")
            setOptional(binding.tvDuaCount, if (dua.repeatCount.isBlank()) "" else "সংখ্যা / পদ্ধতি: ${dua.repeatCount}")
            setOptional(binding.tvDuaVirtue, if (dua.virtue.isBlank()) "" else "ফজিলত / নোট: ${dua.virtue}")
            setOptional(binding.tvDuaGrade, if (dua.grade.isBlank()) "" else "মান: ${dua.grade}")
            setOptional(binding.tvDuaReference, if (dua.reference.isBlank()) "" else "সূত্র: ${dua.reference}")

            val fullContent = buildString {
                append(dua.title).append("\n\n")
                if (dua.arabic.isNotBlank()) append(dua.arabic).append("\n\n")
                if (dua.transliteration.isNotBlank()) append("উচ্চারণ: ").append(dua.transliteration).append("\n")
                if (dua.translation.isNotBlank()) append("অর্থ / বিস্তারিত: ").append(dua.translation).append("\n")
                if (dua.whenToRead.isNotBlank()) append("কখন: ").append(dua.whenToRead).append("\n")
                if (dua.repeatCount.isNotBlank()) append("সংখ্যা / পদ্ধতি: ").append(dua.repeatCount).append("\n")
                if (dua.virtue.isNotBlank()) append("ফজিলত / নোট: ").append(dua.virtue).append("\n")
                if (dua.grade.isNotBlank()) append("মান: ").append(dua.grade).append("\n")
                if (dua.reference.isNotBlank()) append("সূত্র: ").append(dua.reference).append("\n")
                if (dua.sourceUrl.isNotBlank()) append("আরও দেখুন: ").append(dua.sourceUrl).append("\n")
                append("\n- Islamic Alarm App")
            }

            binding.btnCopyDua.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Dua", fullContent))
                Toast.makeText(context, "কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
            binding.btnShareDua.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fullContent)
                }
                context.startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
            }
        }

        private fun setOptional(view: View, value: String) {
            if (value.isBlank()) {
                view.visibility = View.GONE
            } else {
                view.visibility = View.VISIBLE
                (view as? android.widget.TextView)?.text = value
            }
        }

        private fun imageForCategory(category: String): Int = when (category) {
            DuaData.CATEGORY_MORNING_EVENING, DuaData.CATEGORY_AFTER_FARD,
            DuaData.CATEGORY_MOSQUE, DuaData.CATEGORY_FRIDAY -> R.drawable.dua_cat_mosque
            DuaData.CATEGORY_RABBANA, DuaData.CATEGORY_PROPHETS,
            DuaData.CATEGORY_TAWBAH, DuaData.CATEGORY_HADITH -> R.drawable.dua_cat_quran
            DuaData.CATEGORY_SLEEP, DuaData.CATEGORY_NAFL,
            DuaData.CATEGORY_TAHAJJUD, DuaData.CATEGORY_ISTIKHARA -> R.drawable.dua_cat_prayer
            DuaData.CATEGORY_FASTING, DuaData.CATEGORY_RAMADAN,
            DuaData.CATEGORY_HAJJ, DuaData.CATEGORY_CHARITY -> R.drawable.dua_cat_ramadan
            else -> R.drawable.dua_cat_mosque
        }
    }
}
