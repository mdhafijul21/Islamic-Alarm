package com.hafij.islamicalarm.books

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemJamatCategoryBinding

class JamatCategoryAdapter(
    private val categories: List<JamatCategory>,
    private val onCategoryClick: (JamatCategory) -> Unit
) : RecyclerView.Adapter<JamatCategoryAdapter.JamatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JamatViewHolder {
        val binding = ItemJamatCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return JamatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JamatViewHolder, position: Int) {
        val category = categories[position]
        holder.binding.tvJamatTitle.text = category.titleBn
        holder.binding.tvJamatSubtitle.text = category.subtitleBn
        holder.binding.cardJamatCategory.setOnClickListener {
            onCategoryClick(category)
        }
    }

    override fun getItemCount(): Int = categories.size

    class JamatViewHolder(val binding: ItemJamatCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)
}
