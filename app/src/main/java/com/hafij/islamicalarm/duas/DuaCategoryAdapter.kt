package com.hafij.islamicalarm.duas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemDuaCategoryBinding

data class DuaCategory(
    val title: String,
    val count: Int,
    val icon: String
)

class DuaCategoryAdapter(
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<DuaCategoryAdapter.CategoryViewHolder>() {

    private var items: List<DuaCategory> = emptyList()

    fun submitList(newItems: List<DuaCategory>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemDuaCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CategoryViewHolder(
        private val binding: ItemDuaCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DuaCategory) {
            binding.tvCategoryName.text = item.title
            binding.tvCategoryCount.text = "${item.count}টি এন্ট্রি"
            binding.tvCategoryIcon.text = item.icon
            binding.tvCategoryIcon.contentDescription = item.title
            binding.root.setOnClickListener { onCategoryClick(item.title) }
        }
    }
}
