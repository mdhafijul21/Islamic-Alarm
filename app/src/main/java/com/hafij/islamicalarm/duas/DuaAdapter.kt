package com.hafij.islamicalarm.duas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
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

    override fun onBindViewHolder(holder: DuaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DuaViewHolder(private val binding: ItemDuaBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(dua: DuaItem) {
            binding.tvDuaTitle.text = dua.title
            binding.tvDuaCategoryTag.text = dua.category
            binding.tvDuaArabic.text = dua.arabic
            binding.tvDuaTransliteration.text = "উচ্চারণ: ${dua.transliteration}"
            binding.tvDuaTranslation.text = "অর্থ: ${dua.translation}"
            binding.tvDuaReference.text = "সূত্র: ${dua.reference}"

            val fullContent = "${dua.title}\n\n${dua.arabic}\n\nউচ্চারণ: ${dua.transliteration}\nঅর্থ: ${dua.translation}\nসূত্র: ${dua.reference}\n\n- Islamic Alarm App"

            binding.btnCopyDua.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Dua", fullContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "দোআ কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
            }

            binding.btnShareDua.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fullContent)
                }
                context.startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
            }
        }
    }
}
