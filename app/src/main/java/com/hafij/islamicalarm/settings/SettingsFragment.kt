package com.hafij.islamicalarm.settings

import android.Manifest
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
import com.google.android.material.button.MaterialButton
import com.hafij.islamicalarm.MainActivity
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivitySettingsBinding
import com.hafij.islamicalarm.permissions.PermStep
import com.hafij.islamicalarm.permissions.PermissionHelper
import com.hafij.islamicalarm.report.ReportItem
import com.hafij.islamicalarm.report.ReportStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings tab: permission checklist, usage guide and report form. */
class SettingsFragment : Fragment() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!
    private var reportStore: ReportStore? = null

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    private val phoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissions() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivitySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbarSettings.setNavigationOnClickListener {
            (activity as? MainActivity)?.goHome()
        }
        reportStore = ReportStore(requireContext())

        setupGuide()
        setupReport()
        refreshPermissions()
        refreshReports()
        binding.tvAppVersion.text = "সংস্করণ " + appVersion()
    }

    override fun onResume() {
        super.onResume()
        // The user may have just switched something on in the system settings.
        refreshPermissions()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshPermissions()
            refreshReports()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------------------------------------------------------------- permissions

    private fun refreshPermissions() {
        val b = _binding ?: return
        val ctx = context ?: return
        b.layoutPermRows.removeAllViews()

        val steps = PermissionHelper.applicableSteps(ctx)
        var detectable = 0
        var granted = 0

        for (step in steps) {
            if (PermissionHelper.isDetectable(step.id)) {
                detectable++
                val ok = PermissionHelper.isGranted(ctx, step.id)
                if (ok) granted++
                addPermissionRow(step, ok)
            } else {
                addPermissionRow(step, null)
            }
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
        tvDesc.text = if (granted == true) step.why else step.why + "\n▶ " + step.howTo

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
