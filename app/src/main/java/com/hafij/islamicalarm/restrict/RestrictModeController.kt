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
    private val safe = SafeModeController(activity, binding)
    private var currentTab = TAB_ALARM

    fun setup() {
        binding.tabAlarmMode.setOnClickListener { selectTab(TAB_ALARM) }
        binding.tabRestrictMode.setOnClickListener { selectTab(TAB_RESTRICT) }
        binding.tabSafeMode.setOnClickListener { selectTab(TAB_SAFE) }
        binding.btnRestrictEnableService.setOnClickListener {
            PermissionHelper.openSettings(activity, PermissionHelper.ID_ACCESSIBILITY)
        }
        safe.setup()
        selectTab(TAB_ALARM)
    }

    /** Call whenever the alarm screen becomes visible or the app resumes. */
    fun onScreenShown() {
        when (currentTab) {
            TAB_RESTRICT -> refresh()
            TAB_SAFE -> safe.refresh()
        }
    }

    private fun selectTab(tab: Int) {
        currentTab = tab
        binding.layoutAlarmModeContent.visibility = if (tab == TAB_ALARM) View.VISIBLE else View.GONE
        binding.layoutRestrictModeContent.visibility = if (tab == TAB_RESTRICT) View.VISIBLE else View.GONE
        binding.layoutSafeModeContent.visibility = if (tab == TAB_SAFE) View.VISIBLE else View.GONE
        styleTab(binding.tabAlarmMode, tab == TAB_ALARM)
        styleTab(binding.tabRestrictMode, tab == TAB_RESTRICT)
        styleTab(binding.tabSafeMode, tab == TAB_SAFE)
        if (tab == TAB_RESTRICT) refresh()
        if (tab == TAB_SAFE) safe.refresh()
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
            val tvStatusBadge = row.findViewById<TextView>(R.id.tvSlotStatusBadge)
            val tvInfo = row.findViewById<TextView>(R.id.tvSlotInfo)
            val sw = row.findViewById<SwitchCompat>(R.id.switchSlot)
            val btnEdit = row.findViewById<Button>(R.id.btnEditSlot)

            tvTitle.text = "⛔ অ্যাপ ও ব্রাউজার রেস্ট্রিক্ট"
            if (!slot.isConfigured) {
                tvStatusBadge.text = "সেট করা নেই"
                tvStatusBadge.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
                tvInfo.text = "এখনো কোনো সময় বা অ্যাপ বাছাই করা হয়নি।\nনিচের বাটনে চাপ দিয়ে সময়, মেয়াদ এবং যেসব অ্যাপ বা ব্রাউজার লক রাখতে চান তা বাছাই করুন।"
                sw.visibility = View.GONE
                btnEdit.text = "সময় ও অ্যাপ সেট করুন"
            } else {
                val running = slot.activeWindowEnd(now) > 0L
                tvStatusBadge.text = when {
                    running -> "🔴 এখন চলছে"
                    slot.enabled -> "✅ চালু আছে"
                    else -> "⏸️ বন্ধ আছে"
                }
                tvStatusBadge.setTextColor(
                    ContextCompat.getColor(
                        activity,
                        if (running) R.color.error else if (slot.enabled) R.color.secondary else R.color.text_secondary
                    )
                )
                tvInfo.text = describe(slot)
                sw.visibility = View.VISIBLE
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = slot.enabled
                sw.setOnCheckedChangeListener { _, checked ->
                    var updated = slot.copy(enabled = checked)
                    if (checked && !slot.repeatDaily) {
                        updated = updated.copy(onceStartMillis = updated.nextOnceStart(System.currentTimeMillis()))
                    }
                    store.saveSlot(updated)
                    refresh()
                }
                btnEdit.text = "সময় ও অ্যাপ পরিবর্তন করুন"
            }
            btnEdit.setOnClickListener { showEditDialog(slot) }
            row.setOnClickListener { showEditDialog(slot) }
            container.addView(row)
        }
    }

    private fun describe(slot: RestrictSlot): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, slot.hour)
        cal.set(Calendar.MINUTE, slot.minute)
        val time = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)
        val repeat = if (slot.repeatDaily) "প্রতিদিন" else "শুধু একবার"
        return "⏰ শুরুর সময়: $time\n" +
            "⏳ সময়কাল: ${slot.durationMinutes} মিনিট\n" +
            "🔁 ধরন: $repeat\n" +
            "📱 বাছাই করা অ্যাপ/ব্রাউজার: ${slot.packages.size}টি"
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
            .setTitle("রেস্ট্রিক্ট মোড সেটিংস")
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

    companion object {
        private const val TAB_ALARM = 0
        private const val TAB_RESTRICT = 1
        private const val TAB_SAFE = 2
    }
}
