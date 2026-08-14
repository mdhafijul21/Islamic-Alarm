package com.hafij.islamicalarm.audio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ItemAzanSoundBinding

class AzanAdapter(
    private val azanList: List<AzanItem>,
    private var selectedAzanId: String,
    private val onPlayClick: (AzanItem) -> Unit,
    private val onSetAlarmClick: (AzanItem) -> Unit
) : RecyclerView.Adapter<AzanAdapter.AzanViewHolder>() {

    private var currentlyPlayingId: String? = null
    private var isPlaying: Boolean = false

    fun setSelectedAzanId(id: String) {
        selectedAzanId = id
        notifyDataSetChanged()
    }

    fun setPlaybackState(playingId: String?, playing: Boolean) {
        currentlyPlayingId = playingId
        isPlaying = playing
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AzanViewHolder {
        val binding = ItemAzanSoundBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AzanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AzanViewHolder, position: Int) {
        holder.bind(azanList[position])
    }

    override fun getItemCount(): Int = azanList.size

    inner class AzanViewHolder(private val binding: ItemAzanSoundBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AzanItem) {
            val context = binding.root.context
            binding.tvAzanTitleBn.text = item.titleBn
            binding.tvAzanMuazzin.text = "${item.muazzinBn} • ${item.locationBn}"
            binding.tvAzanDuration.text = "সময়: ${item.durationText}"
            binding.tvAzanDescription.text = item.descriptionBn

            val isThisSelected = (item.id == selectedAzanId)
            if (isThisSelected) {
                binding.tvSelectedStatus.text = "বর্তমান এলার্ম টিউন ✅"
                binding.tvSelectedStatus.setTextColor(context.resources.getColor(R.color.secondary, context.theme))
                binding.btnSetAsAlarm.text = "✓ ডিফল্ট টিউন"
                binding.btnSetAsAlarm.isEnabled = false
                binding.btnSetAsAlarm.alpha = 0.6f
            } else {
                binding.tvSelectedStatus.text = "সাধারণ অডিও"
                binding.tvSelectedStatus.setTextColor(context.resources.getColor(R.color.text_secondary, context.theme))
                binding.btnSetAsAlarm.text = "🔔 এলার্মে সেট করুন"
                binding.btnSetAsAlarm.isEnabled = true
                binding.btnSetAsAlarm.alpha = 1.0f
            }

            val isThisPlaying = (item.id == currentlyPlayingId && isPlaying)
            if (isThisPlaying) {
                binding.btnPlayAzan.setImageResource(android.R.drawable.ic_media_pause)
                binding.pbAzanProgress.visibility = View.VISIBLE
                binding.pbAzanProgress.isIndeterminate = true
            } else {
                binding.btnPlayAzan.setImageResource(android.R.drawable.ic_media_play)
                binding.pbAzanProgress.visibility = View.GONE
            }

            binding.btnPlayAzan.setOnClickListener {
                onPlayClick(item)
            }

            binding.btnSetAsAlarm.setOnClickListener {
                onSetAlarmClick(item)
            }
        }
    }
}
