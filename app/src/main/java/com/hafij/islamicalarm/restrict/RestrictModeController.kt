package com.hafij.islamicalarm.restrict

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivityMainBinding
import com.hafij.islamicalarm.permissions.PermissionHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Drives the "এলার্ম | রেস্ট্রিক্ট মোড" tabs at the top of the alarm screen
 * and the five restrict-mode slots.
 */
class RestrictModeController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding
) {

    private val store = RestrictStore(activity)
    private var restrictTabSelected = false

    fun setup() {
        binding.tabAlarmMode.setOnClickListener { selectTab(false) }
        binding.tabRestrictMode.setOnClickListener { selectTab(true) }
        binding.btnRestrictEnableService.setOnClickListener {
            PermissionHelper.openSettings(activity, PermissionHelper.ID_ACCESSIBILITY)
        }
        selectTab(false)
    }

    /** Call whenever the alarm screen becomes visible or the app resumes. */
    fun onScreenShown() {
        if (restrictTabSelected) refresh()
    }

    private fun selectTab(restrict: Boolean) {
        restrictTabSelected = restrict
        binding.layoutAlarmModeContent.visibility = if (restrict) View.GONE else View.VISIBLE
        binding.layoutRestrictModeContent.visibility = if (restrict) View.VISIBLE else View.GONE
        styleTab(binding.tabAlarmMode, !restrict)
        styleTab(binding.tabRestrictMode, restrict)
        if (restrict) refresh()
    }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.setBackgroundResource(if (selected) R.drawable.bg_mode_tab_on else R.drawable.bg_mode_tab_off)
        tab.setTextColor(
            ContextCompat.getColor(activity, if (selected) R.color.primary else R.color.text_secondary)
        )
    }

    fun refresh() {
        store.normalizeExpired()

        val enabled = PermissionHelper.isAccessibilityEnabled(activity)
        if (enabled) {
            binding.tvRestrictServiceStatus.text = "✅ অ্যাক্সেসিবিলিটি সার্ভিস চালু আছে। রেস্ট্রিক্ট মোড কাজ করবে।"
            binding.btnRestrictEnableService.visibility = View.GONE
        } else {
            binding.tvRestrictServiceStatus.text =
                "❌ রেস্ট্রিক্ট মোড কাজ করতে অ্যাক্সেসিবিলিটি সার্ভিস চালু করতে হবে। " +
                    "সুইচ ধূসর হলে: ফোনের Settings > Apps > Islamic Alarm > উপরের ⋮ মেনু > 'Allow restricted settings' দিয়ে আবার চেষ্টা করুন।"
            binding.btnRestrictEnableService.visibility = View.VISIBLE
        }

        val container = binding.layoutRestrictSlots
        container.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        val now = System.currentTimeMillis()

        for (slot in store.getSlots()) {
            val row = inflater.inflate(R.layout.item_restrict_slot, container, false)
            val tvTitle = row.findViewById<TextView>(R.id.tvSlotTitle)
            val tvInfo = row.findViewById<TextView>(R.id.tvSlotInfo)
            val sw = row.findViewById<SwitchCompat>(R.id.switchSlot)

            tvTitle.text = "স্লট " + (slot.index + 1)
            if (!slot.isConfigured) {
                tvInfo.text = "সেট করা নেই। চাপ দিয়ে সময় ও অ্যাপ বাছাই করুন।"
                sw.visibility = View.GONE
            } else {
                val running = slot.activeWindowEnd(now) > 0L
                tvInfo.text = describe(slot) + (if (running) "\n🔴 এখন চলছে" else "")
                sw.visibility = View.VISIBLE
                sw.isChecked = slot.enabled
                sw.setOnCheckedChangeListener { _, checked ->
                    var updated = slot.copy(enabled = checked)
                    if (checked && !slot.repeatDaily) {
                        updated = updated.copy(onceStartMillis = updated.nextOnceStart(System.currentTimeMillis()))
                    }
                    store.saveSlot(updated)
                    refresh()
                }
            }
            row.setOnClickListener { showEditDialog(slot) }
            container.addView(row)
        }
    }

    private fun describe(slot: RestrictSlot): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, slot.hour)
        cal.set(Calendar.MINUTE, slot.minute)
        val time = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)
        val repeat = if (slot.repeatDaily) "প্রতিদিন" else "একবার"
        return "⏰ " + time + " • " + slot.durationMinutes + " মিনিট • " + repeat +
            "\n📱 " + slot.packages.size + "টি অ্যাপ/ব্রাউজার"
    }

    private fun showEditDialog(slot: RestrictSlot) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_restrict_slot, null)
        val timePicker = view.findViewById<TimePicker>(R.id.tpRestrictTime)
        val etDuration = view.findViewById<EditText>(R.id.etRestrictDuration)
        val rbOnce = view.findViewById<RadioButton>(R.id.rbRestrictOnce)
        val rbDaily = view.findViewById<RadioButton>(R.id.rbRestrictDaily)
        val tvApps = view.findViewById<TextView>(R.id.tvRestrictApps)
        val btnPick = view.findViewById<Button>(R.id.btnRestrictPickApps)

        var selected: Set<String> = slot.packages

        if (slot.isConfigured) {
            timePicker.hour = slot.hour
            timePicker.minute = slot.minute
        } else {
            val now = Calendar.getInstance()
            timePicker.hour = now.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = now.get(Calendar.MINUTE)
        }
        etDuration.setText(slot.durationMinutes.toString())
        if (slot.repeatDaily) rbDaily.isChecked = true else rbOnce.isChecked = true

        fun updateAppsText() {
            tvApps.text = if (selected.isEmpty()) {
                "কোনো অ্যাপ বা ব্রাউজার বাছাই করা হয়নি"
            } else {
                selected.size.toString() + "টি অ্যাপ/ব্রাউজার বাছাই করা আছে"
            }
        }
        updateAppsText()

        btnPick.setOnClickListener {
            AppPickerDialog.show(activity, selected) { chosen ->
                selected = chosen
                updateAppsText()
            }
        }

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle("স্লট " + (slot.index + 1))
            .setView(view)
            .setPositiveButton("সংরক্ষণ করুন", null)
            .setNegativeButton("বাতিল", null)
        if (slot.isConfigured) {
            builder.setNeutralButton("মুছুন") { _, _ ->
                store.saveSlot(RestrictSlot(slot.index))
                refresh()
            }
        }
        val dialog = builder.create()
        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val duration = etDuration.text.toString().trim().toIntOrNull()
            if (selected.isEmpty()) {
                Toast.makeText(activity, "কমপক্ষে একটি অ্যাপ বা ব্রাউজার বাছাই করুন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (duration == null || duration < 1 || duration > 1440) {
                etDuration.error = "১ থেকে ১৪৪০ মিনিটের মধ্যে দিন"
                return@setOnClickListener
            }
            val daily = rbDaily.isChecked
            var newSlot = RestrictSlot(
                index = slot.index,
                enabled = true,
                hour = timePicker.hour,
                minute = timePicker.minute,
                durationMinutes = duration,
                repeatDaily = daily,
                packages = selected,
                onceStartMillis = 0L
            )
            if (!daily) {
                newSlot = newSlot.copy(onceStartMillis = newSlot.nextOnceStart(System.currentTimeMillis()))
            }
            store.saveSlot(newSlot)
            dialog.dismiss()
            refresh()

            if (!PermissionHelper.isAccessibilityEnabled(activity)) {
                Toast.makeText(
                    activity,
                    "সংরক্ষিত হয়েছে। কাজ করতে অ্যাক্সেসিবিলিটি সার্ভিস চালু করুন।",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
