package com.hafij.islamicalarm.duas

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
            binding.tvDuaIcon.text = DuaCategoryArtwork.iconFor(dua.category)
            binding.tvDuaIcon.contentDescription = dua.category

            if (dua.arabic.isBlank()) {
                binding.tvDuaArabic.visibility = View.GONE
            } else {
                binding.tvDuaArabic.visibility = View.VISIBLE
                binding.tvDuaArabic.text = dua.arabic
            }

            if (dua.translation.isBlank()) {
                binding.tvDuaTranslation.visibility = View.GONE
            } else {
                binding.tvDuaTranslation.visibility = View.VISIBLE
                binding.tvDuaTranslation.text = "অর্থ: ${dua.translation}"
            }

            binding.tvDuaWhen.visibility =
                if (dua.whenToRead.isBlank()) View.GONE else View.VISIBLE
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
    }
}
