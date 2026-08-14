package com.hafij.islamicalarm.names

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemAllahNameBinding
import com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator

class AllahNameAdapter(private val context: Context) :
    RecyclerView.Adapter<AllahNameAdapter.NameViewHolder>() {

    private var items: List<AllahName> = emptyList()

    fun submitList(newItems: List<AllahName>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NameViewHolder {
        val binding = ItemAllahNameBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NameViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class NameViewHolder(private val binding: ItemAllahNameBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(name: AllahName) {
            binding.tvNameNumber.text = PrayerTimeCalculator.toBengaliNumber(name.number)
            binding.tvNameArabic.text = name.arabic
            binding.tvNameTransliteration.text = name.transliterationBn
            binding.tvNameMeaning.text = name.meaningBn
            binding.tvNameVirtue.text = "✨ ফজিলতঃ " + name.virtueBn
        }
    }
}
