package com.hafij.islamicalarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.data.AlarmItem
import com.hafij.islamicalarm.data.AlarmStore
import com.hafij.islamicalarm.databinding.ActivityMainBinding
import com.hafij.islamicalarm.databinding.DialogAddAlarmBinding
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var alarmStore: AlarmStore
    private lateinit var alarmAdapter: AlarmAdapter

    private val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "এলার্ম চলাকালীন কল রিসিভ করতে ফোন পারমিশন আবশ্যক",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        alarmStore = AlarmStore(this)

        setupRecyclerView()

        binding.fabAddAlarm.setOnClickListener {
            showAddOrEditAlarmDialog(null)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_quran -> {
                    openQuranActivity()
                    true
                }
                R.id.nav_alarms -> true
                else -> false
            }
        }

        loadAlarms()

        // Sequentially check and request all necessary permissions
        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(
            onToggleAlarm = { alarm, isEnabled ->
                val updatedAlarm = alarm.copy(isEnabled = isEnabled)
                alarmStore.updateAlarm(updatedAlarm)
                if (isEnabled) {
                    val triggerMillis = AlarmScheduler.scheduleAlarm(this, updatedAlarm)
                    val toastMsg = AlarmScheduler.getFormattedNextRingTime(this, triggerMillis)
                    Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()
                } else {
                    AlarmScheduler.cancelAlarm(this, updatedAlarm.id)
                    Toast.makeText(this, R.string.alarm_disabled, Toast.LENGTH_SHORT).show()
                }
                loadAlarms()
            },
            onEditAlarm = { alarm ->
                showAddOrEditAlarmDialog(alarm)
            },
            onDeleteAlarm = { alarm ->
                showDeleteConfirmDialog(alarm)
            }
        )

        binding.rvAlarms.layoutManager = LinearLayoutManager(this)
        binding.rvAlarms.adapter = alarmAdapter
    }

    private fun loadAlarms() {
        val alarms = alarmStore.getAlarms()
        if (alarms.isEmpty()) {
            binding.rvAlarms.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvAlarms.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            alarmAdapter.submitList(alarms)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.nav_alarms
    }

    private fun showAddOrEditAlarmDialog(existingAlarm: AlarmItem?) {
        val dialogBinding = DialogAddAlarmBinding.inflate(LayoutInflater.from(this))

        if (existingAlarm != null) {
            dialogBinding.tvDialogTitle.setText(R.string.edit_alarm)
            dialogBinding.timePicker.hour = existingAlarm.hour
            dialogBinding.timePicker.minute = existingAlarm.minute
            dialogBinding.etLockDuration.setText(existingAlarm.lockDurationMinutes.toString())
            dialogBinding.etAlarmLabel.setText(existingAlarm.label)
            if (existingAlarm.isRepeatDaily) {
                dialogBinding.rbDaily.isChecked = true
            } else {
                dialogBinding.rbOnce.isChecked = true
            }
        } else {
            dialogBinding.tvDialogTitle.setText(R.string.add_alarm)
            val now = Calendar.getInstance()
            dialogBinding.timePicker.hour = now.get(Calendar.HOUR_OF_DAY)
            dialogBinding.timePicker.minute = now.get(Calendar.MINUTE)
            dialogBinding.etLockDuration.setText("15")
            dialogBinding.etAlarmLabel.setText("")
            dialogBinding.rbDaily.isChecked = true
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val selectedHour = dialogBinding.timePicker.hour
            val selectedMinute = dialogBinding.timePicker.minute

            val durationInput = dialogBinding.etLockDuration.text.toString().trim()
            var lockDuration = durationInput.toIntOrNull() ?: 15
            if (lockDuration < 1) lockDuration = 1
            if (lockDuration > 60) lockDuration = 60

            val label = dialogBinding.etAlarmLabel.text.toString().trim()
            val isRepeatDaily = dialogBinding.rbDaily.isChecked

            val alarmToSave = existingAlarm?.copy(
                hour = selectedHour,
                minute = selectedMinute,
                lockDurationMinutes = lockDuration,
                label = label,
                isRepeatDaily = isRepeatDaily,
                isEnabled = true
            ) ?: AlarmItem(
                hour = selectedHour,
                minute = selectedMinute,
                lockDurationMinutes = lockDuration,
                label = label,
                isRepeatDaily = isRepeatDaily,
                isEnabled = true
            )

            if (existingAlarm != null) {
                alarmStore.updateAlarm(alarmToSave)
            } else {
                alarmStore.addAlarm(alarmToSave)
            }

            val triggerMillis = AlarmScheduler.scheduleAlarm(this, alarmToSave)
            val toastMsg = AlarmScheduler.getFormattedNextRingTime(this, triggerMillis)
            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show()

            loadAlarms()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog(alarm: AlarmItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_alarm_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancelAlarm(this, alarm.id)
                alarmStore.deleteAlarm(alarm.id)
                Toast.makeText(this, R.string.alarm_deleted, Toast.LENGTH_SHORT).show()
                loadAlarms()
            }
            .show()
    }

    // --- Permission Handling Sequential Flow ---

    private fun checkAndRequestPermissions() {
        checkExactAlarmPermission {
            checkBatteryOptimizationPermission {
                checkOemAutostartPermission {
                    checkPhoneStatePermission()
                }
            }
        }
    }

    private fun checkExactAlarmPermission(onNext: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_exact_alarm_title)
                    .setMessage(R.string.permission_exact_alarm_msg)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onNext()
                    }
                    .setNegativeButton(R.string.skip) { _, _ -> onNext() }
                    .setCancelable(false)
                    .show()
                return
            }
        }
        onNext()
    }

    private fun checkBatteryOptimizationPermission(onNext: () -> Unit) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_battery_title)
                .setMessage(R.string.permission_battery_msg)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onNext()
                }
                .setNegativeButton(R.string.skip) { _, _ -> onNext() }
                .setCancelable(false)
                .show()
            return
        }
        onNext()
    }

    private fun checkOemAutostartPermission(onNext: () -> Unit) {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ENGLISH)
        val knownManufacturers = listOf("xiaomi", "redmi", "oppo", "realme", "vivo", "huawei", "honor", "asus", "letv")

        val isMatchingOem = knownManufacturers.any { manufacturer.contains(it) }

        if (isMatchingOem && !alarmStore.hasSeenAutostartDialog()) {
            val oemName = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val message = getString(R.string.permission_autostart_msg, oemName)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_autostart_title)
                .setMessage(message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    alarmStore.setSeenAutostartDialog(true)
                    openOemAutostartSettings()
                    onNext()
                }
                .setNegativeButton(R.string.skip) { _, _ ->
                    alarmStore.setSeenAutostartDialog(true)
                    onNext()
                }
                .setCancelable(false)
                .show()
            return
        }
        onNext()
    }

    private fun openOemAutostartSettings() {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ENGLISH)
        val intents = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                intents.add(Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intents.add(Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
            }
            manufacturer.contains("vivo") -> {
                intents.add(Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
                intents.add(Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents.add(Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                intents.add(Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
            }
            manufacturer.contains("asus") -> {
                intents.add(Intent().setComponent(android.content.ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")))
            }
            manufacturer.contains("letv") -> {
                intents.add(Intent().setComponent(android.content.ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutostartManageActivity")))
            }
        }

        var started = false
        for (intent in intents) {
            try {
                startActivity(intent)
                started = true
                break
            } catch (e: Exception) {
                // Try next Intent
            }
        }

        if (!started) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    private fun openQuranActivity() {
        val intent = Intent(this, com.hafij.islamicalarm.quran.QuranActivity::class.java)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return if (item.itemId == R.id.action_quran) {
            openQuranActivity()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}
