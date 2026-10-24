package com.hafij.islamicalarm.quran

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemSurahHeaderBinding

class SurahHeaderAdapter(
    private val arabicName: String,
    private val detailsText: String,
    private val showBismillah: Boolean
) : RecyclerView.Adapter<SurahHeaderAdapter.HeaderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemSurahHeaderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.binding.tvHeaderSurahArabic.text = arabicName
        holder.binding.tvHeaderSurahDetails.text = detailsText
        holder.binding.layoutBismillah.visibility = if (showBismillah) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = 1

    class HeaderViewHolder(val binding: ItemSurahHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}
