package com.hafij.islamicalarm.quran

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemParaBinding

class ParaAdapter(
    private var paraList: List<Para>,
    private val onParaClick: (Para) -> Unit
) : RecyclerView.Adapter<ParaAdapter.ParaViewHolder>() {

    fun updateData(newList: List<Para>) {
        paraList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParaViewHolder {
        val binding = ItemParaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParaViewHolder, position: Int) {
        holder.bind(paraList[position])
    }

    override fun getItemCount(): Int = paraList.size

    inner class ParaViewHolder(private val binding: ItemParaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(para: Para) {
            binding.tvParaNumber.text = toBengaliNumber(para.id)
            binding.tvParaBangla.text = para.nameBangla
            binding.tvParaMeaning.text = "পারা ${toBengaliNumber(para.id)} • ${para.meaningBangla}"
            binding.tvParaArabic.text = para.nameArabic

            binding.root.setOnClickListener {
                onParaClick(para)
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
