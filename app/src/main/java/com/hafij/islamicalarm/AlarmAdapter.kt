package com.hafij.islamicalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hafij.islamicalarm.data.AlarmItem
import com.hafij.islamicalarm.databinding.ItemAlarmBinding

class AlarmAdapter(
    private val onToggleAlarm: (AlarmItem, Boolean) -> Unit,
    private val onEditAlarm: (AlarmItem) -> Unit,
    private val onDeleteAlarm: (AlarmItem) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    private var alarmsList = listOf<AlarmItem>()

    fun submitList(newList: List<AlarmItem>) {
        alarmsList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(alarmsList[position])
    }

    override fun getItemCount(): Int = alarmsList.size

    inner class AlarmViewHolder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: AlarmItem) {
            binding.tvAlarmTime.text = alarm.getFormattedTime()

            if (alarm.label.isNotBlank()) {
                binding.tvAlarmLabel.text = alarm.label
                binding.tvAlarmLabel.visibility = View.VISIBLE
            } else {
                binding.tvAlarmLabel.visibility = View.GONE
            }

            val lockText = "🔒 ${alarm.lockDurationMinutes} ${binding.root.context.getString(R.string.minutes_suffix)}"
            binding.tvLockDuration.text = lockText

            // Temporarily clear listener to prevent triggering during bind
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = alarm.isEnabled

            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleAlarm(alarm, isChecked)
            }

            binding.btnEdit.setOnClickListener {
                onEditAlarm(alarm)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteAlarm(alarm)
            }
        }
    }
}
