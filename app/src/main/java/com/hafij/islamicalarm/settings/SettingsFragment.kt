package com.hafij.islamicalarm.settings

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivitySettingsBinding
import com.hafij.islamicalarm.permissions.PermStep
import com.hafij.islamicalarm.permissions.PermissionHelper
import com.hafij.islamicalarm.report.ReportItem
import com.hafij.islamicalarm.report.ReportStore
import com.hafij.islamicalarm.widget.IslamicAlarmWidget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings tab: permission checklist, usage guide and report form. */
class SettingsFragment : Fragment() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!
    private var reportStore: ReportStore? = null
    private var lastPermSignature: String? = null

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions(force = true) }

    private val phoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions(force = true) }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissions(force = true) }

    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissions(force = true)
        if (granted) {
            context?.let { ctx ->
                if (com.hafij.islamicalarm.auto.AutoAlarmManager.isAutoModeEnabled(ctx)) {
                    com.hafij.islamicalarm.auto.AutoAlarmManager.syncAutoMode(ctx, fromForeground = true)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivitySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var activeCategory: Int = -1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbarSettings.setNavigationOnClickListener {
            handleBack()
        }
        reportStore = ReportStore(requireContext())

        setupCategories()
        setupGuide()
        setupReport()
        refreshPermissions(force = true)
        refreshReports()
        binding.tvAppVersion.text = "ইসলামিক এলার্ম • সংস্করণ " + appVersion()
    }

    fun handleBack(): Boolean {
        if (_binding == null) return false
        if (activeCategory != -1) {
            activeCategory = -1
            renderCategoryScreen()
            return true
        }
        return false
    }

    private fun setupCategories() {
        val b = _binding ?: return
        b.rowCatPermissionsHeader.setOnClickListener {
            activeCategory = 0
            renderCategoryScreen()
        }
        b.rowCatGuideHeader.setOnClickListener {
            activeCategory = 1
            renderCategoryScreen()
        }
        b.rowCatReportHeader.setOnClickListener {
            activeCategory = 2
            renderCategoryScreen()
        }
        b.btnSettingsAddToHome.setOnClickListener {
            pinPrayerCounterWidget()
        }
        b.cardAddToHomeWidget.setOnClickListener {
            pinPrayerCounterWidget()
        }
        b.btnSettingsShareApp.setOnClickListener {
            shareAppLink()
        }
        b.cardSettingsShareApp.setOnClickListener {
            shareAppLink()
        }
        refreshShareUi()
        syncAdminShareLinkInBackground()
        renderCategoryScreen()
    }

    private fun shareAppLink() {
        val ctx = context ?: return
        val shareIntent = AppShareConfigStore.buildShareIntent(ctx)
        startActivity(Intent.createChooser(shareIntent, "অ্যাপ শেয়ার করুন"))
    }

    private fun refreshShareUi() {
        val b = _binding ?: return
        val ctx = context ?: return
        val cfg = AppShareConfigStore.getConfig(ctx)
        b.tvShareLinkSummary.text = if (cfg.hasCustomAdminLink) {
            "আপডেটকৃত লিংক: ${cfg.shareUrl}"
        } else {
            "বন্ধু ও পরিবারের সাথে অ্যাপের লিংক শেয়ার করুন"
        }
    }

    private fun syncAdminShareLinkInBackground() {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val updated = AppShareConfigStore.syncFromAdminEndpointIfConfigured(ctx)
            if (updated && _binding != null) {
                refreshShareUi()
            }
        }
    }

    private fun pinPrayerCounterWidget() {
        val ctx = context ?: return
        IslamicAlarmWidget.refreshAllWidgets(ctx)
        val appWidgetManager = AppWidgetManager.getInstance(ctx)
        val provider = ComponentName(ctx, IslamicAlarmWidget::class.java)
        val supported = try {
            appWidgetManager.isRequestPinAppWidgetSupported
        } catch (_: Exception) {
            false
        }

        if (supported) {
            val callbackIntent = Intent(ctx, IslamicAlarmWidget::class.java).apply {
                action = IslamicAlarmWidget.ACTION_WIDGET_PINNED
            }
            val successCallback = PendingIntent.getBroadcast(
                ctx,
                405,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val requested = try {
                appWidgetManager.requestPinAppWidget(provider, null, successCallback)
            } catch (_: Exception) {
                false
            }
            if (requested) {
                Toast.makeText(
                    ctx,
                    "হোম স্ক্রিনে টাইম কাউন্টার যোগ করার অনুমতি নিশ্চিত করুন",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("⏱️ হোম স্ক্রিনে টাইম কাউন্টার যোগ করুন")
            .setMessage(
                "আপনার ফোনের লঞ্চারে সরাসরি পিন সাপোর্ট না করলে নিচের সহজ ধাপ অনুসরণ করুন:\n\n" +
                    "১. ফোনের হোম স্ক্রিনের খালি জায়গায় একটু চেপে ধরুন।\n" +
                    "২. Widgets অপশনে প্রবেশ করুন।\n" +
                    "৩. \"Islamic Alarm\" খুঁজে হোম স্ক্রিনে টেনে ছেড়ে দিন — পরবর্তী নামাজের লাইভ কাউন্টার দেখাবে।"
            )
            .setPositiveButton("বুঝেছি", null)
            .show()
    }

    private fun renderCategoryScreen() {
        val b = _binding ?: return
        val inDetail = activeCategory != -1
        b.layoutSettingsMenu.visibility = if (inDetail) View.GONE else View.VISIBLE
        b.layoutSettingsDetail.visibility = if (inDetail) View.VISIBLE else View.GONE

        b.layoutCatPermissionsBody.visibility = if (activeCategory == 0) View.VISIBLE else View.GONE
        b.layoutCatGuideBody.visibility = if (activeCategory == 1) View.VISIBLE else View.GONE
        b.layoutCatReportBody.visibility = if (activeCategory == 2) View.VISIBLE else View.GONE

        if (inDetail) {
            b.toolbarSettings.setNavigationIcon(R.drawable.ic_arrow_back_24)
            b.toolbarSettings.setNavigationIconTint(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.secondary)
            )
            b.toolbarSettings.title = when (activeCategory) {
                0 -> "১. পারমিশন"
                1 -> "২. কিভাবে ব্যবহার করবেন"
                else -> "৩. আপনার মতামত / রিপোর্ট"
            }
        } else {
            b.toolbarSettings.navigationIcon = null
            b.toolbarSettings.title = "সেটিংস"
        }
        b.settingsScrollView.scrollTo(0, 0)
    }

    override fun onResume() {
        super.onResume()
        // The user may have just switched something on in the system settings.
        refreshPermissions()
        refreshShareUi()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            view?.post {
                if (_binding == null || !isAdded || isHidden) return@post
                refreshPermissions()
                refreshReports()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lastPermSignature = null
        _binding = null
    }

    // ---------------------------------------------------------------- permissions

    private fun refreshPermissions(force: Boolean = false) {
        val b = _binding ?: return
        val ctx = context ?: return

        val steps = PermissionHelper.applicableSteps(ctx) + PermissionHelper.microphoneStep
        val statuses = steps.map { step ->
            if (PermissionHelper.isDetectable(step.id)) {
                PermissionHelper.isGranted(ctx, step.id)
            } else {
                null
            }
        }
        val signature = statuses.joinToString(",") { it?.toString() ?: "u" }
        if (!force && signature == lastPermSignature) return
        lastPermSignature = signature

        b.layoutPermRows.removeAllViews()

        var detectable = 0
        var granted = 0

        steps.forEachIndexed { idx, step ->
            val st = statuses[idx]
            if (st != null) {
                detectable++
                if (st) granted++
            }
            addPermissionRow(step, st)
        }
        addPermissionRow(PermissionHelper.screenPinStep, null)

        b.tvPermSummary.text = "$granted/$detectable টি অনুমতি চালু আছে"
    }

    private fun addPermissionRow(step: PermStep, granted: Boolean?) {
        val b = _binding ?: return
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_permission_row, b.layoutPermRows, false)
        val tvStatus = row.findViewById<TextView>(R.id.tvPermStatus)
        val tvTitle = row.findViewById<TextView>(R.id.tvPermTitle)
        val tvDesc = row.findViewById<TextView>(R.id.tvPermDesc)
        val btn = row.findViewById<MaterialButton>(R.id.btnPermAction)

        tvStatus.text = when (granted) {
            true -> "✅"
            false -> "❌"
            null -> "❓"
        }
        tvTitle.text = step.title
        tvDesc.text = if (granted == true) step.why else step.why + "\n\n" + step.howTo

        if (granted == true) {
            btn.visibility = View.GONE
        } else {
            btn.visibility = View.VISIBLE
            btn.text = if (granted == null) "সেটিংস খুলুন" else "চালু করুন"
            btn.setOnClickListener { requestPermission(step.id) }
        }
        b.layoutPermRows.addView(row)
    }

    private fun requestPermission(id: String) {
        val act = activity ?: return
        when (id) {
            PermissionHelper.ID_LOCATION -> askRuntime(
                id,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                { PermissionHelper.openAppDetails(act) }
            )
            PermissionHelper.ID_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    askRuntime(
                        id,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        { PermissionHelper.openSettings(act, PermissionHelper.ID_NOTIFICATIONS) }
                    )
                } else {
                    PermissionHelper.openSettings(act, id)
                }
            }
            PermissionHelper.ID_PHONE -> askRuntime(
                id,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                { PermissionHelper.openAppDetails(act) }
            )
            PermissionHelper.ID_MICROPHONE -> askRuntime(
                id,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                { PermissionHelper.openAppDetails(act) }
            )
            PermissionHelper.ID_AUTOSTART -> {
                PermissionHelper.markAutostartDone(act)
                PermissionHelper.openSettings(act, id)
            }
            else -> PermissionHelper.openSettings(act, id)
        }
    }

    /**
     * Android shows a runtime-permission dialog only a limited number of times.
     * After the user has denied it for good, the only way is the system settings page.
     */
    private fun askRuntime(
        id: String,
        perms: Array<String>,
        launch: () -> Unit,
        fallback: () -> Unit
    ) {
        val act = activity ?: return
        val prefs = act.getSharedPreferences("settings_perm_asked", Context.MODE_PRIVATE)
        val askedBefore = prefs.getBoolean(id, false)
        val canAskAgain = perms.any { ActivityCompat.shouldShowRequestPermissionRationale(act, it) }
        if (askedBefore && !canAskAgain) {
            fallback()
        } else {
            prefs.edit().putBoolean(id, true).apply()
            launch()
        }
    }

    // ---------------------------------------------------------------- guide

    private fun setupGuide() {
        val b = _binding ?: return
        val inflater = LayoutInflater.from(requireContext())
        GuideData.sections.forEachIndexed { index, section ->
            val item = inflater.inflate(R.layout.item_guide_section, b.layoutGuide, false)
            val tvTitle = item.findViewById<TextView>(R.id.tvGuideTitle)
            val tvBody = item.findViewById<TextView>(R.id.tvGuideBody)
            tvBody.text = section.second

            var expanded = index == 0
            fun render() {
                tvTitle.text = (if (expanded) "▾  " else "▸  ") + section.first
                tvBody.visibility = if (expanded) View.VISIBLE else View.GONE
            }
            render()
            item.setOnClickListener {
                expanded = !expanded
                render()
            }
            b.layoutGuide.addView(item)
        }
    }

    // ---------------------------------------------------------------- report

    private fun setupReport() {
        binding.btnSendReport.setOnClickListener {
            val b = _binding ?: return@setOnClickListener
            val store = reportStore ?: return@setOnClickListener
            val message = b.etReportMessage.text.toString().trim()
            if (message.length < 5) {
                b.etReportMessage.error = "কমপক্ষে ৫ অক্ষর লিখুন"
                return@setOnClickListener
            }
            val type = if (b.rbSuggestion.isChecked) {
                ReportStore.TYPE_SUGGESTION
            } else {
                ReportStore.TYPE_PROBLEM
            }
            store.add(type, message, deviceInfo(), appVersion())
            b.etReportMessage.setText("")
            Toast.makeText(requireContext(), "রিপোর্ট সংরক্ষণ করা হয়েছে", Toast.LENGTH_SHORT).show()
            refreshReports()
        }
    }

    private fun refreshReports() {
        val b = _binding ?: return
        val store = reportStore ?: return
        b.layoutReports.removeAllViews()
        val reports = store.getAll()
        b.tvReportsEmpty.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
        for (report in reports) {
            val row = inflater.inflate(R.layout.item_report_row, b.layoutReports, false)
            val typeText = if (report.type == ReportStore.TYPE_SUGGESTION) "💡 পরামর্শ" else "⚠️ সমস্যা"
            val statusText = if (report.synced) "পাঠানো হয়েছে" else "ফোনে সংরক্ষিত"
            row.findViewById<TextView>(R.id.tvReportMeta).text =
                typeText + " • " + dateFormat.format(Date(report.createdAt)) + " • " + statusText
            row.findViewById<TextView>(R.id.tvReportText).text = report.message
            row.findViewById<MaterialButton>(R.id.btnReportShare).setOnClickListener {
                shareReport(report)
            }
            row.findViewById<MaterialButton>(R.id.btnReportDelete).setOnClickListener {
                store.delete(report.id)
                refreshReports()
            }
            b.layoutReports.addView(row)
        }
    }

    private fun shareReport(report: ReportItem) {
        val typeText = if (report.type == ReportStore.TYPE_SUGGESTION) "পরামর্শ" else "সমস্যা"
        val text = "ইসলামিক এলার্ম রিপোর্ট\n" +
            "ধরন: " + typeText + "\n" +
            "বার্তা: " + report.message + "\n" +
            "ডিভাইস: " + report.device + "\n" +
            "অ্যাপ সংস্করণ: " + report.appVersion
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "রিপোর্ট পাঠান"))
    }

    private fun deviceInfo(): String =
        Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE +
            " (API " + Build.VERSION.SDK_INT + ")"

    private fun appVersion(): String {
        return try {
            val ctx = requireContext()
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
}
