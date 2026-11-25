package com.hafij.islamicalarm.restrict

import android.content.Intent
import android.net.VpnService
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.databinding.ActivityMainBinding
import com.hafij.islamicalarm.permissions.PermissionHelper

/**
 * Drives the "সেফ মোড" tab:
 *  - Master Safe Mode switch (shows a notice dialog with "ওকে" to confirm turning off)
 *  - System-wide Local DNS VPN switch (Cloudflare/AdGuard Family DNS + local blocklist)
 *  - Android Native Private DNS direct-apply check & honest fallback dialog
 *  - Incognito / Private Tab block toggle
 *  - Custom blocked words/domains editor
 */
class SafeModeController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val onRequestVpnConsent: (Intent) -> Unit = { intent -> activity.startActivity(intent) }
) {

    private val store = SafeModeStore(activity)

    fun setup() {
        binding.btnSafeEnableService.setOnClickListener {
            PermissionHelper.openSettings(activity, PermissionHelper.ID_ACCESSIBILITY)
        }
        binding.cardSafePermGuide.setOnClickListener {
            com.hafij.islamicalarm.permissions.PermissionImageZoomDialog.show(
                activity,
                PermissionHelper.accessibilityStep,
                com.hafij.islamicalarm.R.drawable.guide_perm_accessibility
            )
        }
        binding.btnSafeWords.setOnClickListener { showWordsDialog() }
        binding.btnSetupPrivateDns.setOnClickListener { onPrivateDnsDirectClicked() }
    }

    fun onVpnConsentResult() {
        val stillNeedsPrep = try {
            VpnService.prepare(activity) != null
        } catch (_: Exception) {
            true
        }
        if (!stillNeedsPrep) {
            store.isEnabled = true
            store.isDnsVpnEnabled = true
            SafeDnsVpnService.start(activity)
            Toast.makeText(activity, "✅ সিস্টেম-ওয়াইড সেফ DNS VPN চালু হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            store.isDnsVpnEnabled = false
            Toast.makeText(activity, "VPN অনুমতি না দেওয়ায় লোকাল DNS ব্লকার চালু হয়নি", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    fun refresh() {
        val serviceOn = PermissionHelper.isAccessibilityEnabled(activity)
        val privateDns = SafeDnsHelper.getPrivateDnsStatus(activity)
        val externalVpnActive = SafeDnsHelper.isExternalVpnActive(activity)

        // Master Safe Mode switch
        binding.switchSafeMode.setOnCheckedChangeListener(null)
        binding.switchSafeMode.isChecked = store.isEnabled
        binding.switchSafeMode.setOnCheckedChangeListener { _, checked -> onToggle(checked) }

        binding.tvSafeStatus.text = when {
            store.isEnabled && serviceOn ->
                "✅ সেফ মোড চালু আছে। সার্চ করলে অযথা বন্ধ হবে না—অ্যাডাল্ট সাইটে প্রবেশ বা ভিডিও চালু হলে সাথে সাথে ব্লার হয়ে ব্যাক এ আসবে।"
            store.isEnabled && !serviceOn ->
                "⚠️ সেফ মোড চালু, কিন্তু ইনকগনিটো ব্লক ও সাইট-ক্লিক সুরক্ষার জন্য অ্যাক্সেসিবিলিটি সার্ভিস চালু করুন।"
            else -> "সেফ মোড বন্ধ আছে।"
        }
        binding.btnSafeEnableService.visibility = if (serviceOn) View.GONE else View.VISIBLE
        binding.cardSafePermGuide.visibility = if (serviceOn) View.GONE else View.VISIBLE

        // System-wide Local DNS VPN switch
        binding.switchSafeDnsVpn.setOnCheckedChangeListener(null)
        binding.switchSafeDnsVpn.isChecked = store.isEnabled && store.isDnsVpnEnabled
        binding.switchSafeDnsVpn.setOnCheckedChangeListener { _, checked -> onToggleDnsVpn(checked) }

        val needsVpnPrep = try {
            VpnService.prepare(activity) != null
        } catch (_: Exception) {
            false
        }
        binding.tvSafeDnsStatus.text = when {
            store.isEnabled && store.isDnsVpnEnabled && SafeDnsVpnService.isVpnRunning -> {
                val base = "✅ সিস্টেম-ওয়াইড সেফ DNS VPN সক্রিয় (Family DNS) • ব্লক হয়েছে: ${store.dnsBlockedCount}টি"
                val last = store.lastBlockedDomain
                if (last.isNullOrBlank()) base else "$base • সর্বশেষ: $last"
            }
            privateDns.isFamilySafe ->
                "✅ ফোনের Private DNS (${privateDns.serverName}) সক্রিয় থাকায় লোকাল VPN ছাড়াই DNS সুরক্ষা কাজ করছে (অন্য VPN-ও চলবে)।"
            externalVpnActive ->
                "🌐 আপনার অন্য একটি VPN চালু আছে — সেফ মোডের ইনকগনিটো ও সাইট-ব্লার সুরক্ষা সক্রিয় রয়েছে।"
            store.isEnabled && store.isDnsVpnEnabled && needsVpnPrep ->
                "⚠️ সেফ DNS VPN চালু করতে অনুমতির পপ-আপে 'OK' চাপুন।"
            store.isEnabled && store.isDnsVpnEnabled ->
                "✅ সেফ DNS VPN সক্রিয় • ব্লক হয়েছে: ${store.dnsBlockedCount}টি"
            else ->
                "বন্ধ আছে • চালু করলে ফোনের সব অ্যাপ ও ব্রাউজারে নেটওয়ার্ক পর্যায়ে অ্যাডাল্ট ডোমেইন ব্লক ও সেফ সার্চ সুরক্ষা কাজ করবে।"
        }

        // Native Private DNS status helper
        binding.tvPrivateDnsStatus.text = when {
            !privateDns.isSupported ->
                "এই ফিচারটি Android 9 বা তার উপরের ভার্সনে পাওয়া যায়। এই ডিভাইসে উপরের লোকাল সেফ DNS VPN ব্যবহার করুন।"
            privateDns.isFamilySafe ->
                "✅ সক্রিয়: ${privateDns.serverName} — স্ট্যাটাস বারে কোনো VPN চিহ্ন নেই এবং অন্য যেকোনো VPN একসাথে চলবে।"
            privateDns.isActive && !privateDns.serverName.isNullOrBlank() ->
                "বর্তমানে '${privateDns.serverName}' চালু আছে। অ্যাডাল্ট সাইট ব্লকের জন্য '${SafeDnsHelper.HOST_ADGUARD_FAMILY}' সেট করা দরকার।"
            else ->
                "চালু নেই • অ্যান্ড্রয়েড নিরাপত্তা নীতির কারণে সাধারণ অ্যাপ সিস্টেমের Private DNS সরাসরি বদলাতে পারে না; পারমিশন না থাকলে ভেতরের ১-ক্লিক সেফ DNS ব্যবহার করুন।"
        }
        binding.btnSetupPrivateDns.text = if (privateDns.isFamilySafe) {
            "Private DNS সক্রিয় আছে"
        } else {
            "সরাসরি Private DNS চালু করুন"
        }

        // Incognito block switch
        binding.switchIncognitoBlock.setOnCheckedChangeListener(null)
        binding.switchIncognitoBlock.isChecked = store.isIncognitoBlockEnabled
        binding.switchIncognitoBlock.setOnCheckedChangeListener { _, checked ->
            store.isIncognitoBlockEnabled = checked
            refresh()
        }

        binding.tvSafeWordsCount.text = "আপনার যোগ করা শব্দ/ওয়েবসাইট: " + store.getCustomWords().size + "টি"
    }

    private fun onToggle(checked: Boolean) {
        if (checked) {
            store.isEnabled = true
            val privateDns = SafeDnsHelper.getPrivateDnsStatus(activity)
            val externalVpnActive = SafeDnsHelper.isExternalVpnActive(activity)
            when {
                privateDns.isFamilySafe -> {
                    store.isDnsVpnEnabled = false
                    SafeDnsVpnService.stop(activity)
                    Toast.makeText(
                        activity,
                        "✅ সেফ মোড চালু • Private DNS (${privateDns.serverName}) সক্রিয় আছে",
                        Toast.LENGTH_LONG
                    ).show()
                }
                externalVpnActive -> {
                    store.isDnsVpnEnabled = false
                    Toast.makeText(
                        activity,
                        "✅ সেফ মোড চালু • আপনার অন্য VPN চলতে থাকবে",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    store.isDnsVpnEnabled = true
                    startOrRequestDnsVpn()
                }
            }
            if (!PermissionHelper.isAccessibilityEnabled(activity)) {
                Toast.makeText(
                    activity,
                    "ইনকগনিটো ও সাইট-ক্লিক সুরক্ষার জন্য অ্যাক্সেসিবিলিটি সার্ভিস চালু করুন",
                    Toast.LENGTH_LONG
                ).show()
            }
            refresh()
        } else {
            refresh()
            MaterialAlertDialogBuilder(activity)
                .setTitle("⚠️ সেফ মোড বন্ধ করার নোটিশ")
                .setMessage(
                    "সেফ মোড বন্ধ করলে নিচের সুরক্ষাগুলো বন্ধ হয়ে যাবে:\n" +
                        "• সিস্টেম-ওয়াইড সেফ DNS / লোকাল VPN ব্লকার\n" +
                        "• ব্রাউজারে ইনকগনিটো / প্রাইভেট ট্যাব ব্লক\n" +
                        "• অ্যাডাল্ট সাইটে ক্লিক বা VidMate, SnapTube, Telegram-এ নিষিদ্ধ ভিডিও শনাক্ত করে ব্লার-ব্যাক\n\n" +
                        "আপনি কি নিশ্চিতভাবে সেফ মোড বন্ধ করতে চান? বন্ধ করতে 'ওকে' চাপুন।"
                )
                .setPositiveButton("ওকে") { _, _ ->
                    store.isEnabled = false
                    store.isDnsVpnEnabled = false
                    SafeDnsVpnService.stop(activity)
                    refresh()
                }
                .setNegativeButton("বাতিল") { _, _ -> refresh() }
                .setOnCancelListener { refresh() }
                .show()
        }
    }

    private fun onToggleDnsVpn(checked: Boolean) {
        if (checked) {
            if (SafeDnsHelper.isExternalVpnActive(activity)) {
                refresh()
                MaterialAlertDialogBuilder(activity)
                    .setTitle("🌐 অন্য একটি VPN চালু আছে")
                    .setMessage(
                        "অ্যান্ড্রয়েডে একসাথে দুটি VPN চালানো যায় না। লোকাল সেফ DNS VPN চালু করলে আপনার বর্তমান VPN-টি বন্ধ হয়ে যাবে।\n\n" +
                            "আপনি চাইলে Private DNS ব্যবহার করতে পারেন—তাহলে কোনো VPN চিহ্ন ছাড়াই অন্য VPN-ও একসাথে চলবে।"
                    )
                    .setPositiveButton("লোকাল DNS চালু করুন") { _, _ ->
                        store.isEnabled = true
                        store.isDnsVpnEnabled = true
                        startOrRequestDnsVpn()
                        refresh()
                    }
                    .setNeutralButton("Private DNS অপশন") { _, _ ->
                        onPrivateDnsDirectClicked()
                    }
                    .setNegativeButton("বাতিল") { _, _ -> refresh() }
                    .setOnCancelListener { refresh() }
                    .show()
                return
            }
            store.isEnabled = true
            store.isDnsVpnEnabled = true
            startOrRequestDnsVpn()
            refresh()
        } else {
            refresh()
            MaterialAlertDialogBuilder(activity)
                .setTitle("⚠️ সেফ DNS / লোকাল VPN বন্ধ করবেন?")
                .setMessage(
                    "সিস্টেম-ওয়াইড সেফ DNS / লোকাল VPN বন্ধ করলেও ইনকগনিটো ও সাইট-ব্লার সুরক্ষা চালু থাকবে এবং আপনি অন্য VPN বা Private DNS ব্যবহার করতে পারবেন।\n\n" +
                        "বন্ধ করতে চাইলে 'ওকে' চাপুন।"
                )
                .setPositiveButton("ওকে") { _, _ ->
                    store.isDnsVpnEnabled = false
                    SafeDnsVpnService.stop(activity)
                    refresh()
                }
                .setNegativeButton("বাতিল") { _, _ -> refresh() }
                .setOnCancelListener { refresh() }
                .show()
        }
    }

    private fun onPrivateDnsDirectClicked() {
        val current = SafeDnsHelper.getPrivateDnsStatus(activity)
        if (current.isFamilySafe) {
            Toast.makeText(
                activity,
                "✅ Private DNS (${current.serverName}) ইতিমধ্যেই চালু আছে!",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        when (SafeDnsHelper.applyPrivateDnsDirectly(activity, SafeDnsHelper.HOST_ADGUARD_FAMILY)) {
            SafeDnsHelper.DirectApplyResult.APPLIED -> {
                Toast.makeText(
                    activity,
                    "✅ ফোনের Settings-এ Private DNS (${SafeDnsHelper.HOST_ADGUARD_FAMILY}) সরাসরি সেট হয়ে গেছে!",
                    Toast.LENGTH_LONG
                ).show()
                refresh()
            }
            SafeDnsHelper.DirectApplyResult.UNSUPPORTED -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Android 9+ প্রয়োজন")
                    .setMessage("সিস্টেম Private DNS ফিচারটি Android 9 বা তার উপরের ভার্সনে সমর্থিত। এই ডিভাইসে অ্যাপের লোকাল সেফ DNS চালু করুন।")
                    .setPositiveButton("লোকাল সেফ DNS চালু করুন") { _, _ ->
                        store.isEnabled = true
                        store.isDnsVpnEnabled = true
                        startOrRequestDnsVpn()
                        refresh()
                    }
                    .setNegativeButton("বন্ধ করুন", null)
                    .show()
            }
            SafeDnsHelper.DirectApplyResult.REQUIRES_SYSTEM_SETTINGS -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("🔒 অ্যান্ড্রয়েড সিস্টেম সীমাবদ্ধতা")
                    .setMessage(
                        "অ্যান্ড্রয়েড অপারেটিং সিস্টেমের নিরাপত্তা নিয়ম অনুযায়ী কোনো সাধারণ অ্যাপ সিস্টেম-অনুমতি (WRITE_SECURE_SETTINGS) ছাড়া সরাসরি ফোনের Private DNS সেটিংস বদলাতে পারে না।\n\n" +
                            "তাই আপনার জন্য দুটি বাস্তব উপায় রয়েছে:\n" +
                            "১) অ্যাপের ভেতরের লোকাল সেফ DNS চালু করুন—কোনো সেটিংসে যেতে হবে না, সরাসরি এখান থেকেই চালু হবে।\n" +
                            "২) অথবা কেবল একবার ফোনের Settings → Network → Private DNS-এ নিজে '${SafeDnsHelper.HOST_ADGUARD_FAMILY}' বসালে আর কখনো VPN আইকন দেখাবে না এবং অন্য VPN-ও চলবে।"
                    )
                    .setPositiveButton("অ্যাপের সেফ DNS চালু করুন") { _, _ ->
                        store.isEnabled = true
                        store.isDnsVpnEnabled = true
                        startOrRequestDnsVpn()
                        refresh()
                    }
                    .setNeutralButton("Settings খুলুন") { _, _ ->
                        SafeDnsHelper.copyHostname(activity, SafeDnsHelper.HOST_ADGUARD_FAMILY)
                        SafeDnsHelper.openPrivateDnsSettings(activity)
                    }
                    .setNegativeButton("বন্ধ করুন", null)
                    .show()
            }
        }
    }

    private fun startOrRequestDnsVpn() {
        val prepIntent = try {
            VpnService.prepare(activity)
        } catch (_: Exception) {
            null
        }
        if (prepIntent == null) {
            SafeDnsVpnService.start(activity)
        } else {
            onRequestVpnConsent(prepIntent)
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
            .setMessage("সার্চ রেজাল্ট পেজে নয়—যখন কোনো ওয়েবসাইটে প্রবেশ করবেন বা ভিডিও প্লেয়ারে এই শব্দ/সাইট থাকবে, তখন স্ক্রিন ব্লার করে অটো ব্যাক করবে এবং DNS VPN ডোমেইনটি আটকাবে।")
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
