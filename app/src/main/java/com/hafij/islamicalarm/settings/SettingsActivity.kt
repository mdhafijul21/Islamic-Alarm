package com.hafij.islamicalarm.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.hafij.islamicalarm.R
import com.hafij.islamicalarm.databinding.ActivitySettingsBinding
import com.hafij.islamicalarm.permissions.PermStep
import com.hafij.islamicalarm.permissions.PermissionHelper
import com.hafij.islamicalarm.report.ReportItem
import com.hafij.islamicalarm.report.ReportStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var reportStore: ReportStore

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    private val phoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarSettings.setNavigationOnClickListener { finish() }

        reportStore = ReportStore(this)

        setupGuide()
        setupReport()
        refreshPermissions()
        refreshReports()
        binding.tvAppVersion.text = "সংস্করণ ${appVersion()}"
    }

    override fun onResume() {
        super.onResume()
        // The user may have just switched something on in the system settings.
        refreshPermissions()
    }

    // ---------------------------------------------------------------- permissions

    private fun refreshPermissions() {
        val container = binding.layoutPermRows
        container.removeAllViews()

        val steps = PermissionHelper.applicableSteps(this)
        var detectable = 0
        var granted = 0

        for (step in steps) {
            if (PermissionHelper.isDetectable(step.id)) {
                detectable++
                val ok = PermissionHelper.isGranted(this, step.id)
                if (ok) granted++
                addPermissionRow(step, ok)
            } else {
                addPermissionRow(step, null)
            }
        }
        addPermissionRow(PermissionHelper.screenPinStep, null)

        binding.tvPermSummary.text = "$granted/$detectable টি অনুমতি চালু আছে"
    }

    private fun addPermissionRow(step: PermStep, granted: Boolean?) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_permission_row, binding.layoutPermRows, false)
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
        binding.layoutPermRows.addView(row)
    }

    private fun requestPermission(id: String) {
        when (id) {
            PermissionHelper.ID_LOCATION -> askRuntime(
                id,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                { PermissionHelper.openAppDetails(this) }
            )
            PermissionHelper.ID_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    askRuntime(
                        id,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        { PermissionHelper.openSettings(this, PermissionHelper.ID_NOTIFICATIONS) }
                    )
                } else {
                    PermissionHelper.openSettings(this, id)
                }
            }
            PermissionHelper.ID_PHONE -> askRuntime(
                id,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                { PermissionHelper.openAppDetails(this) }
            )
            PermissionHelper.ID_AUTOSTART -> {
                PermissionHelper.markAutostartDone(this)
                PermissionHelper.openSettings(this, id)
            }
            else -> PermissionHelper.openSettings(this, id)
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
        val prefs = getSharedPreferences("settings_perm_asked", MODE_PRIVATE)
        val askedBefore = prefs.getBoolean(id, false)
        val canAskAgain = perms.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
        if (askedBefore && !canAskAgain) {
            fallback()
        } else {
            prefs.edit().putBoolean(id, true).apply()
            launch()
        }
    }

    // ---------------------------------------------------------------- guide

    private fun setupGuide() {
        val inflater = LayoutInflater.from(this)
        GuideData.sections.forEachIndexed { index, section ->
            val item = inflater.inflate(R.layout.item_guide_section, binding.layoutGuide, false)
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
            binding.layoutGuide.addView(item)
        }
    }

    // ---------------------------------------------------------------- report

    private fun setupReport() {
        binding.btnSendReport.setOnClickListener {
            val message = binding.etReportMessage.text.toString().trim()
            if (message.length < 5) {
                binding.etReportMessage.error = "কমপক্ষে ৫ অক্ষর লিখুন"
                return@setOnClickListener
            }
            val type = if (binding.rbSuggestion.isChecked) {
                ReportStore.TYPE_SUGGESTION
            } else {
                ReportStore.TYPE_PROBLEM
            }
            reportStore.add(type, message, deviceInfo(), appVersion())
            binding.etReportMessage.setText("")
            Toast.makeText(this, "রিপোর্ট সংরক্ষণ করা হয়েছে", Toast.LENGTH_SHORT).show()
            refreshReports()
        }
    }

    private fun refreshReports() {
        val container = binding.layoutReports
        container.removeAllViews()
        val reports = reportStore.getAll()
        binding.tvReportsEmpty.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
        for (report in reports) {
            val row = inflater.inflate(R.layout.item_report_row, container, false)
            val typeText = if (report.type == ReportStore.TYPE_SUGGESTION) "💡 পরামর্শ" else "⚠️ সমস্যা"
            val statusText = if (report.synced) "পাঠানো হয়েছে" else "ফোনে সংরক্ষিত"
            row.findViewById<TextView>(R.id.tvReportMeta).text =
                typeText + " • " + dateFormat.format(Date(report.createdAt)) + " • " + statusText
            row.findViewById<TextView>(R.id.tvReportText).text = report.message
            row.findViewById<MaterialButton>(R.id.btnReportShare).setOnClickListener {
                shareReport(report)
            }
            row.findViewById<MaterialButton>(R.id.btnReportDelete).setOnClickListener {
                reportStore.delete(report.id)
                refreshReports()
            }
            container.addView(row)
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
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
}
