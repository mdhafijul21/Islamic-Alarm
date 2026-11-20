package com.hafij.islamicalarm.restrict

import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.databinding.ActivityMainBinding
import com.hafij.islamicalarm.permissions.PermissionHelper

/** The "সেফ মোড" tab: on/off switch, service status and the user's own blocked words. */
class SafeModeController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding
) {

    private val store = SafeModeStore(activity)

    fun setup() {
        binding.btnSafeEnableService.setOnClickListener {
            PermissionHelper.openSettings(activity, PermissionHelper.ID_ACCESSIBILITY)
        }
        binding.btnSafeWords.setOnClickListener { showWordsDialog() }
    }

    fun refresh() {
        val serviceOn = PermissionHelper.isAccessibilityEnabled(activity)

        // Set the switch without triggering its listener.
        binding.switchSafeMode.setOnCheckedChangeListener(null)
        binding.switchSafeMode.isChecked = store.isEnabled
        binding.switchSafeMode.setOnCheckedChangeListener { _, checked -> onToggle(checked) }

        binding.tvSafeStatus.text = when {
            store.isEnabled && serviceOn -> "✅ সেফ মোড চালু আছে।"
            store.isEnabled && !serviceOn ->
                "⚠️ সেফ মোড চালু, কিন্তু অ্যাক্সেসিবিলিটি সার্ভিস বন্ধ, তাই কাজ করছে না। নিচের বাটনে সার্ভিস চালু করুন।"
            else -> "সেফ মোড বন্ধ আছে।"
        }
        binding.btnSafeEnableService.visibility = if (serviceOn) View.GONE else View.VISIBLE
        binding.tvSafeWordsCount.text = "আপনার যোগ করা শব্দ: " + store.getCustomWords().size + "টি"
    }

    private fun onToggle(checked: Boolean) {
        if (checked) {
            store.isEnabled = true
            if (!PermissionHelper.isAccessibilityEnabled(activity)) {
                Toast.makeText(activity, "কাজ করতে অ্যাক্সেসিবিলিটি সার্ভিস চালু করুন", Toast.LENGTH_LONG).show()
            }
            refresh()
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("সেফ মোড বন্ধ করবেন?")
                .setMessage("বন্ধ করলে সার্চের ফিল্টার আর কাজ করবে না।")
                .setPositiveButton("বন্ধ করুন") { _, _ ->
                    store.isEnabled = false
                    refresh()
                }
                .setNegativeButton("চালু থাকুক") { _, _ -> refresh() }
                .setOnCancelListener { refresh() }
                .show()
        }
    }

    private fun showWordsDialog() {
        val density = activity.resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val input = EditText(activity).apply {
            setText(store.getCustomWords().joinToString("\n"))
            hint = "প্রতি লাইনে একটি শব্দ বা সাইটের নাম"
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
        }
        val box = FrameLayout(activity)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(input)

        MaterialAlertDialogBuilder(activity)
            .setTitle("নিজের শব্দ যোগ করুন")
            .setMessage("সার্চ রেজাল্ট বা খোলা পেজে এই শব্দ/সাইট শনাক্ত হলে সেফ মোড সতর্ক করবে। শুধু টাইপ করলে চালু হবে না।")
            .setView(box)
            .setPositiveButton("সংরক্ষণ করুন") { _, _ ->
                val words = input.text.toString().split('\n', ',')
                store.setCustomWords(words)
                refresh()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }
}
