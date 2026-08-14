package com.hafij.islamicalarm.prayertimes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.databinding.ItemLocationEntryBinding

class LocationAdapter(
    private var locations: List<District>,
    private val onLocationClick: (District) -> Unit
) : RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {

    fun updateList(newLocations: List<District>) {
        this.locations = newLocations
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemLocationEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(locations[position])
    }

    override fun getItemCount(): Int = locations.size

    inner class LocationViewHolder(private val binding: ItemLocationEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: District) {
            binding.tvLocationName.text = item.nameBn

            binding.tvLocationIcon.text = when (item.type) {
                LocationType.DIVISION -> "🏛️"
                LocationType.DISTRICT -> "📍"
                LocationType.THANA -> "🏘️"
                LocationType.GLOBAL_CITY -> "🌍"
                LocationType.CUSTOM_GPS -> "📡"
            }

            binding.tvLocationSub.text = when (item.type) {
                LocationType.DIVISION -> "বাংলাদেশ • বিভাগীয় প্রধান শহর"
                LocationType.DISTRICT -> "${item.divisionBn} বিভাগ • বাংলাদেশ"
                LocationType.THANA -> "${item.parentBn} জেলা • ${item.divisionBn} বিভাগ"
                LocationType.GLOBAL_CITY -> "${item.parentBn} • আন্তর্জাতিক শহর"
                LocationType.CUSTOM_GPS -> "স্বয়ংক্রিয় GPS শনাক্তকরণ (${item.parentBn})"
            }

            binding.tvLocationTypeTag.text = when (item.type) {
                LocationType.DIVISION -> "বিভাগ"
                LocationType.DISTRICT -> "জেলা"
                LocationType.THANA -> "থানা/উপজেলা"
                LocationType.GLOBAL_CITY -> "আন্তর্জাতিক"
                LocationType.CUSTOM_GPS -> "GPS"
            }

            binding.root.setOnClickListener {
                onLocationClick(item)
            }
        }
    }
}
