package com.hafij.islamicalarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.amal.AmalTrackerActivity
import com.hafij.islamicalarm.data.AlarmItem
import com.hafij.islamicalarm.data.AlarmStore
import com.hafij.islamicalarm.databinding.ActivityMainBinding
import com.hafij.islamicalarm.databinding.DialogAddAlarmBinding
import com.hafij.islamicalarm.databinding.DialogLocationPickerBinding
import com.hafij.islamicalarm.names.AllahNamesActivity
import com.hafij.islamicalarm.prayertimes.CalculationMethod
import com.hafij.islamicalarm.prayertimes.District
import com.hafij.islamicalarm.prayertimes.HijriCalendarActivity
import com.hafij.islamicalarm.prayertimes.HijriCalendarHelper
import com.hafij.islamicalarm.prayertimes.LocationAdapter
import com.hafij.islamicalarm.prayertimes.LocationData
import com.hafij.islamicalarm.prayertimes.LocationHelper
import com.hafij.islamicalarm.prayertimes.LocationType
import com.hafij.islamicalarm.prayertimes.PrayerSchedule
import com.hafij.islamicalarm.prayertimes.PrayerTimeCalculator
import com.hafij.islamicalarm.tahajjud.TahajjudCalculatorActivity
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var alarmStore: AlarmStore
    private lateinit var alarmAdapter: AlarmAdapter
    private lateinit var prefs: SharedPreferences

    private var selectedDistrict: District = LocationData.getDefaultDistrict()
    private var currentSchedule: PrayerSchedule? = null
    private var countdownTimer: CountDownTimer? = null

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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            Toast.makeText(this, "📡 লোকেশন অনুমতি পাওয়া গেছে। GPS অনুযায়ী সময় সেট করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            autoDetectLocationAndSetPrayerTimes(showToast = true)
        } else {
            Toast.makeText(
                this,
                "লোকেশন পারমিশন ছাড়া ডিফল্ট (${selectedDistrict.nameBn}) সময়সূচী অনুযায়ী চলবে।",
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
        prefs = getSharedPreferences("islamic_alarm_app_prefs", Context.MODE_PRIVATE)

        // Load saved location
        selectedDistrict = loadSavedDistrict()

        setupRecyclerView()
        setupPrayerTimesHub()
        setupBottomNavigation()

        binding.fabAddAlarm.setOnClickListener {
            showAddOrEditAlarmDialog(null)
        }

        binding.switchAutoSilent.isChecked =
            com.hafij.islamicalarm.silent.AutoSilentManager.isAutoSilentEnabled(this)
        binding.switchAutoSilent.setOnCheckedChangeListener { _, isChecked ->
            com.hafij.islamicalarm.silent.AutoSilentManager.setAutoSilentEnabled(this, isChecked)
            if (isChecked && !com.hafij.islamicalarm.silent.AutoSilentManager.hasDndPermission(this)) {
                Toast.makeText(this, "সাইলেন্ট মোডের জন্য DND পারমিশন চালু করুন", Toast.LENGTH_SHORT).show()
                com.hafij.islamicalarm.silent.AutoSilentManager.requestDndPermission(this)
            }
        }

        loadAlarms()

        // Check & request runtime permissions on launch (Location, Overlay, Exact Alarm, Battery, Autostart, Phone)
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePrayerTimes()
        loadAlarms()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }

    private fun loadSavedDistrict(): District {
        val savedNameBn = prefs.getString("selected_district_bn", null)
        if (savedNameBn != null) {
            val lat = prefs.getFloat("selected_district_lat", 0f).toDouble()
            val lng = prefs.getFloat("selected_district_lng", 0f).toDouble()
            val tz = prefs.getFloat("selected_district_tz", 6f).toDouble()
            val offset = prefs.getInt("selected_district_offset", 0)
            val nameEn = prefs.getString("selected_district_en", "") ?: ""
            val parentBn = prefs.getString("selected_district_parent", "") ?: ""
            val typeStr = prefs.getString("selected_district_type", LocationType.DISTRICT.name)
            val type = try {
                LocationType.valueOf(typeStr ?: LocationType.DISTRICT.name)
            } catch (e: Exception) {
                LocationType.DISTRICT
            }
            val methodStr = prefs.getString("selected_district_method", CalculationMethod.KARACHI_BD.name)
            val method = try {
                CalculationMethod.valueOf(methodStr ?: CalculationMethod.KARACHI_BD.name)
            } catch (e: Exception) {
                CalculationMethod.KARACHI_BD
            }

            if (lat != 0.0 && lng != 0.0) {
                return District(
                    nameEn = nameEn,
                    nameBn = savedNameBn,
                    divisionBn = parentBn,
                    lat = lat,
                    lng = lng,
                    offsetMinutes = offset,
                    timeZone = tz,
                    type = type,
                    parentBn = parentBn,
                    method = method
                )
            }
            return LocationData.findDistrict(savedNameBn)
        }
        return LocationData.getDefaultDistrict()
    }

    private fun saveSelectedDistrict(district: District) {
        selectedDistrict = district
        prefs.edit()
            .putString("selected_district_bn", district.nameBn)
            .putString("selected_district_en", district.nameEn)
            .putString("selected_district_parent", district.parentBn)
            .putFloat("selected_district_lat", district.lat.toFloat())
            .putFloat("selected_district_lng", district.lng.toFloat())
            .putFloat("selected_district_tz", district.timeZone.toFloat())
            .putInt("selected_district_offset", district.offsetMinutes)
            .putString("selected_district_type", district.type.name)
            .putString("selected_district_method", district.method.name)
            .apply()
    }

    private fun autoDetectLocationAndSetPrayerTimes(showToast: Boolean = true) {
        if (!LocationHelper.hasLocationPermission(this)) {
            requestLocationPermissionDirect()
            return
        }

        if (showToast) {
            Toast.makeText(this, "📡 বর্তমান GPS লোকেশন সনাক্ত করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        }

        LocationHelper.fetchCurrentLocation(
            context = this,
            onSuccess = { detectedDistrict ->
                saveSelectedDistrict(detectedDistrict)
                updatePrayerTimes()
                if (showToast) {
                    Toast.makeText(
                        this,
                        "✅ লোকেশন সফলভাবে সেট হয়েছে: ${detectedDistrict.displayName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onError = { errorMsg ->
                if (showToast) {
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun requestLocationPermissionDirect() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        locationPermissionLauncher.launch(permissions)
    }

    private fun setupBottomNavigation() {
        // Set default selection to Option 1: Prayer Times
        binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
        showPrayerTimesScreen()

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_prayer_times -> {
                    showPrayerTimesScreen()
                    true
                }
                R.id.nav_alarms -> {
                    showAlarmsScreen()
                    true
                }
                R.id.nav_quran -> {
                    openQuranActivity()
                    false
                }
                R.id.nav_tasbih -> {
                    openTasbihActivity()
                    false
                }
                R.id.nav_duas -> {
                    openDuasActivity()
                    false
                }
                else -> false
            }
        }
    }

    private fun showPrayerTimesScreen() {
        binding.layoutPrayerTimesContainer.visibility = View.VISIBLE
        binding.layoutAlarmsContainer.visibility = View.GONE
        binding.fabAddAlarm.visibility = View.GONE
        binding.toolbar.title = "নামাজের সময়সূচী"
        binding.toolbar.subtitle = selectedDistrict.displayName
        updatePrayerTimes()
    }

    private fun showAlarmsScreen() {
        binding.layoutPrayerTimesContainer.visibility = View.GONE
        binding.layoutAlarmsContainer.visibility = View.VISIBLE
        binding.fabAddAlarm.visibility = View.VISIBLE
        binding.toolbar.title = "এলার্ম ও সাইলেন্ট মোড"
        binding.toolbar.subtitle = "নামাজের সময় মোবাইল লক এলার্ম"
        loadAlarms()
    }

    private fun setupPrayerTimesHub() {
        // District Selector
        binding.btnSelectDistrict.setOnClickListener {
            showDistrictSelectionDialog()
        }

        // Quick GPS Button
        binding.btnQuickGpsLocation.setOnClickListener {
            autoDetectLocationAndSetPrayerTimes(showToast = true)
        }

        // Quick feature cards
        binding.cardFeatureAmal.setOnClickListener {
            startActivity(Intent(this, AmalTrackerActivity::class.java))
        }

        binding.cardFeatureTahajjud.setOnClickListener {
            startActivity(Intent(this, TahajjudCalculatorActivity::class.java))
        }

        binding.cardFeatureNames.setOnClickListener {
            startActivity(Intent(this, AllahNamesActivity::class.java))
        }

        binding.cardFeatureCalendar.setOnClickListener {
            startActivity(Intent(this, HijriCalendarActivity::class.java))
        }

        binding.cardFeatureBooks.setOnClickListener {
            startActivity(Intent(this, com.hafij.islamicalarm.books.IslamicLibraryActivity::class.java))
        }

        binding.cardFeatureRamadan.setOnClickListener {
            startActivity(Intent(this, com.hafij.islamicalarm.ramadan.RamadanActivity::class.java))
        }

        binding.cardFeatureAudioQuran.setOnClickListener {
            openAudioQuranActivity()
        }

        binding.cardFeatureAzan.setOnClickListener {
            openAzanAudioActivity()
        }

        // Waqt Alarm quick buttons
        binding.btnAlarmFajr.setOnClickListener {
            currentSchedule?.let { sch ->
                showQuickAlarmDialog(
                    sch.fajrCal.get(Calendar.HOUR_OF_DAY),
                    sch.fajrCal.get(Calendar.MINUTE),
                    "ফজর সালাত"
                )
            }
        }

        binding.btnAlarmDhuhr.setOnClickListener {
            currentSchedule?.let { sch ->
                showQuickAlarmDialog(
                    sch.dhuhrCal.get(Calendar.HOUR_OF_DAY),
                    sch.dhuhrCal.get(Calendar.MINUTE),
                    "যোহর সালাত"
                )
            }
        }

        binding.btnAlarmAsr.setOnClickListener {
            currentSchedule?.let { sch ->
                showQuickAlarmDialog(
                    sch.asrCal.get(Calendar.HOUR_OF_DAY),
                    sch.asrCal.get(Calendar.MINUTE),
                    "আসর সালাত"
                )
            }
        }

        binding.btnAlarmMaghrib.setOnClickListener {
            currentSchedule?.let { sch ->
                showQuickAlarmDialog(
                    sch.maghribCal.get(Calendar.HOUR_OF_DAY),
                    sch.maghribCal.get(Calendar.MINUTE),
                    "মাগরিব সালাত ও ইফতার"
                )
            }
        }

        binding.btnAlarmIsha.setOnClickListener {
            currentSchedule?.let { sch ->
                showQuickAlarmDialog(
                    sch.ishaCal.get(Calendar.HOUR_OF_DAY),
                    sch.ishaCal.get(Calendar.MINUTE),
                    "ইশা ও তারাবিহ/বিতর"
                )
            }
        }
    }

    private fun showDistrictSelectionDialog() {
        val dialogBinding = DialogLocationPickerBinding.inflate(LayoutInflater.from(this))

        var currentFilteredList = LocationData.allLocations
        val adapter = LocationAdapter(currentFilteredList) { chosenLocation ->
            saveSelectedDistrict(chosenLocation)
            updatePrayerTimes()
            Toast.makeText(this, "${chosenLocation.displayName} এর সময়সূচী সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.rvLocationList.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvLocationList.adapter = adapter

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        val itemClickListener: (District) -> Unit = { chosen ->
            saveSelectedDistrict(chosen)
            updatePrayerTimes()
            dialog.dismiss()
            Toast.makeText(this, "${chosen.displayName} এর সময়সূচী সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
        }

        val updatedAdapter = LocationAdapter(currentFilteredList, itemClickListener)
        dialogBinding.rvLocationList.adapter = updatedAdapter

        dialogBinding.btnCloseLocationDialog.setOnClickListener {
            dialog.dismiss()
        }

        // Auto GPS Detect Button
        dialogBinding.btnDetectGpsLocation.setOnClickListener {
            dialog.dismiss()
            autoDetectLocationAndSetPrayerTimes(showToast = true)
        }

        // Search Filter
        fun filterLocations() {
            val query = dialogBinding.etSearchLocation.text?.toString() ?: ""
            val selectedChipId = dialogBinding.chipGroupLocationFilter.checkedChipId

            var baseList = when (selectedChipId) {
                R.id.chipDistricts -> LocationData.districts
                R.id.chipThanas -> LocationData.thanas
                R.id.chipDivisions -> LocationData.divisions
                R.id.chipGlobal -> LocationData.globalCities
                else -> LocationData.allLocations
            }

            if (query.isNotBlank()) {
                val q = query.trim().lowercase(Locale.ROOT)
                baseList = baseList.filter {
                    it.nameBn.lowercase(Locale.ROOT).contains(q) ||
                    it.nameEn.lowercase(Locale.ROOT).contains(q) ||
                    it.divisionBn.lowercase(Locale.ROOT).contains(q) ||
                    it.parentBn.lowercase(Locale.ROOT).contains(q)
                }
            }

            updatedAdapter.updateList(baseList)
        }

        dialogBinding.etSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterLocations()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.chipGroupLocationFilter.setOnCheckedStateChangeListener { _, _ ->
            filterLocations()
        }

        // Online Search
        dialogBinding.btnSearchOnline.setOnClickListener {
            val query = dialogBinding.etSearchLocation.text?.toString()?.trim() ?: ""
            if (query.isBlank()) {
                Toast.makeText(this, "অনুগ্রহ করে অনুসন্ধানের জন্য শহর বা দেশের নাম লিখুন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogBinding.pbLocationLoading.visibility = View.VISIBLE
            lifecycleScope.launch {
                val onlineResults = LocationHelper.searchOnline(this@MainActivity, query)
                dialogBinding.pbLocationLoading.visibility = View.GONE
                if (onlineResults.isNotEmpty()) {
                    updatedAdapter.updateList(onlineResults)
                    Toast.makeText(this@MainActivity, "${onlineResults.size}টি অনলাইন ফলাফল পাওয়া গেছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "'$query' এর জন্য অনলাইন তথ্য পাওয়া যায়নি। অফলাইন তালিকা ব্যবহার করুন।", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun updatePrayerTimes() {
        val now = Calendar.getInstance()
        val schedule = PrayerTimeCalculator.calculate(now, selectedDistrict)
        currentSchedule = schedule

        binding.btnSelectDistrict.text = "📍 ${selectedDistrict.nameBn}"

        if (binding.layoutPrayerTimesContainer.visibility == View.VISIBLE) {
            binding.toolbar.subtitle = selectedDistrict.displayName
        }

        // Hijri Date
        val hijriDate = HijriCalendarHelper.getHijriDate(now)
        binding.tvMainHijriDate.text = hijriDate.formattedBn

        // Times
        binding.tvFajrTime.text = "শুরু: ভোর ${schedule.fajr} — সূর্যোদয়: ভোর ${schedule.sunrise}"
        binding.tvDhuhrTime.text = "শুরু: দুপুর ${schedule.dhuhr} — আসর শুরু পর্যন্ত"
        binding.tvAsrTime.text = "শুরু: বিকাল ${schedule.asr} — সূর্যাস্ত: সন্ধ্যা ${schedule.sunset}"
        binding.tvMaghribTime.text = "শুরু: সন্ধ্যা ${schedule.maghrib} (ইফতার) — ইশা শুরু পর্যন্ত"
        binding.tvIshaTime.text = "শুরু: রাত ${schedule.isha} — ফজর শুরু পর্যন্ত"

        binding.tvCurrentWaqtStatus.text = PrayerTimeCalculator.getCurrentWaqtName(now, schedule)

        startPrayerCountdownTicker()
    }

    private fun startPrayerCountdownTicker() {
        countdownTimer?.cancel()
        val schedule = currentSchedule ?: return
        val now = Calendar.getInstance()
        val nextPrayer = PrayerTimeCalculator.getNextPrayer(now, schedule)

        binding.tvNextPrayerTitle.text = "পরবর্তী নামাজ: ${nextPrayer.prayerNameBn} (${nextPrayer.prayerTimeStr})"

        countdownTimer = object : CountDownTimer(nextPrayer.remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvNextPrayerCountdown.text = "বাকি " + PrayerTimeCalculator.formatCountdown(millisUntilFinished)
            }

            override fun onFinish() {
                updatePrayerTimes()
            }
        }.start()
    }

    private fun showQuickAlarmDialog(hour: Int, minute: Int, defaultLabel: String) {
        val newAlarm = AlarmItem(
            hour = hour,
            minute = minute,
            label = defaultLabel,
            lockDurationMinutes = 15,
            isRepeatDaily = true,
            isEnabled = true
        )
        showAddOrEditAlarmDialog(newAlarm)
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
        alarmAdapter.submitList(alarms)

        if (alarms.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvAlarms.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvAlarms.visibility = View.VISIBLE
        }
    }

    private fun showAddOrEditAlarmDialog(existingAlarm: AlarmItem?) {
        val isEditing = existingAlarm != null
        val dialogBinding = DialogAddAlarmBinding.inflate(LayoutInflater.from(this))

        val calendar = Calendar.getInstance()
        var selectedHour = existingAlarm?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)
        var selectedMinute = existingAlarm?.minute ?: calendar.get(Calendar.MINUTE)

        dialogBinding.tvDialogTitle.text = if (isEditing) getString(R.string.edit_alarm) else getString(R.string.add_alarm)

        dialogBinding.timePicker.setIs24HourView(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dialogBinding.timePicker.hour = selectedHour
            dialogBinding.timePicker.minute = selectedMinute
        } else {
            @Suppress("DEPRECATION")
            dialogBinding.timePicker.currentHour = selectedHour
            @Suppress("DEPRECATION")
            dialogBinding.timePicker.currentMinute = selectedMinute
        }

        if (isEditing) {
            dialogBinding.etAlarmLabel.setText(existingAlarm.label)
            dialogBinding.etLockDuration.setText(existingAlarm.lockDurationMinutes.toString())
            if (existingAlarm.isRepeatDaily) {
                dialogBinding.rbDaily.isChecked = true
            } else {
                dialogBinding.rbOnce.isChecked = true
            }
        } else {
            dialogBinding.etLockDuration.setText("15")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                selectedHour = dialogBinding.timePicker.hour
                selectedMinute = dialogBinding.timePicker.minute
            } else {
                @Suppress("DEPRECATION")
                selectedHour = dialogBinding.timePicker.currentHour
                @Suppress("DEPRECATION")
                selectedMinute = dialogBinding.timePicker.currentMinute
            }

            val label = dialogBinding.etAlarmLabel.text.toString().trim()
            val lockDurationStr = dialogBinding.etLockDuration.text.toString().trim()
            val lockDuration = lockDurationStr.toIntOrNull() ?: 15
            val isRepeatDaily = dialogBinding.rbDaily.isChecked

            if (lockDuration !in 1..60) {
                dialogBinding.etLockDuration.error = "লক ডিউরেশন ১ থেকে ৬০ মিনিটের মধ্যে হতে হবে"
                return@setOnClickListener
            }

            val alarm = if (isEditing) {
                existingAlarm!!.copy(
                    hour = selectedHour,
                    minute = selectedMinute,
                    label = label,
                    lockDurationMinutes = lockDuration,
                    isRepeatDaily = isRepeatDaily,
                    isEnabled = true
                )
            } else {
                AlarmItem(
                    hour = selectedHour,
                    minute = selectedMinute,
                    label = label,
                    lockDurationMinutes = lockDuration,
                    isRepeatDaily = isRepeatDaily,
                    isEnabled = true
                )
            }

            if (isEditing) {
                alarmStore.updateAlarm(alarm)
            } else {
                alarmStore.addAlarm(alarm)
            }

            val triggerMillis = AlarmScheduler.scheduleAlarm(this@MainActivity, alarm)
            val toastMsg = AlarmScheduler.getFormattedNextRingTime(this@MainActivity, triggerMillis)
            Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_LONG).show()

            loadAlarms()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog(alarm: AlarmItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_alarm_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancelAlarm(this, alarm.id)
                alarmStore.deleteAlarm(alarm.id)
                loadAlarms()
                Toast.makeText(this, R.string.alarm_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkAndRequestPermissions() {
        checkLocationPermission {
            checkOverlayPermission {
                checkExactAlarmPermission {
                    checkBatteryOptimization {
                        checkAutostartPermission {
                            checkPhoneStatePermission()
                        }
                    }
                }
            }
        }
    }

    private fun checkLocationPermission(onNext: () -> Unit) {
        if (!LocationHelper.hasLocationPermission(this)) {
            val hasAskedLocation = prefs.getBoolean("has_asked_location", false)
            if (!hasAskedLocation) {
                prefs.edit().putBoolean("has_asked_location", true).apply()

                MaterialAlertDialogBuilder(this)
                    .setTitle("📍 সঠিক নামাজের সময় পেতে লোকেশন অনুমতি দিন")
                    .setMessage("আপনার বর্তমান জেলা, থানা বা শহরের সঠিক নামাজের সময়সূচী স্বয়ংক্রিয়ভাবে সেট করার জন্য লোকেশন পারমিশন প্রয়োজন।")
                    .setPositiveButton("অনুমতি দিন") { _, _ ->
                        requestLocationPermissionDirect()
                        onNext()
                    }
                    .setNegativeButton("পরে দিব") { _, _ ->
                        onNext()
                    }
                    .setCancelable(false)
                    .show()
                return
            }
        } else {
            // Already has permission -> auto sync location silently on startup
            autoDetectLocationAndSetPrayerTimes(showToast = false)
        }
        onNext()
    }

    private fun checkOverlayPermission(onNext: () -> Unit) {
        if (!Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("ডিসপ্লে ওভারলে পারমিশন আবশ্যক")
                .setMessage("ফোনের স্ক্রিন লক থাকা অবস্থায় নামাজের এলার্ম স্ক্রিন প্রদর্শন করতে 'Display over other apps' পারমিশন চালু করুন।")
                .setPositiveButton(R.string.grant_permission) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    onNext()
                }
                .setNegativeButton(R.string.skip) { _, _ ->
                    onNext()
                }
                .setCancelable(false)
                .show()
            return
        }
        onNext()
    }

    private fun checkExactAlarmPermission(onNext: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_exact_alarm_title)
                    .setMessage(R.string.permission_exact_alarm_msg)
                    .setPositiveButton(R.string.grant_permission) { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        onNext()
                    }
                    .setNegativeButton(R.string.skip) { _, _ ->
                        onNext()
                    }
                    .setCancelable(false)
                    .show()
                return
            }
        }
        onNext()
    }

    private fun checkBatteryOptimization(onNext: () -> Unit) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_battery_title)
                .setMessage(R.string.permission_battery_msg)
                .setPositiveButton(R.string.grant_permission) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(intent)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                    onNext()
                }
                .setNegativeButton(R.string.skip) { _, _ ->
                    onNext()
                }
                .setCancelable(false)
                .show()
            return
        }
        onNext()
    }

    private fun checkAutostartPermission(onNext: () -> Unit) {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ENGLISH)
        val knownManufacturers = listOf("xiaomi", "redmi", "oppo", "vivo", "realme", "huawei", "honor", "asus", "letv")

        val hasAskedAutostart = prefs.getBoolean("has_asked_autostart", false)
        if (!hasAskedAutostart && knownManufacturers.any { manufacturer.contains(it) }) {
            prefs.edit().putBoolean("has_asked_autostart", true).apply()

            val manufacturerName = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val msg = getString(R.string.permission_autostart_msg, manufacturerName)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_autostart_title)
                .setMessage(msg)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    openOemAutostartSettings()
                    onNext()
                }
                .setNegativeButton(R.string.skip) { _, _ ->
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
                // Try next
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

    private fun openTasbihActivity() {
        val intent = Intent(this, com.hafij.islamicalarm.tasbih.TasbihActivity::class.java)
        startActivity(intent)
    }

    private fun openDuasActivity() {
        val intent = Intent(this, com.hafij.islamicalarm.duas.DuasActivity::class.java)
        startActivity(intent)
    }

    private fun openLibraryActivity() {
        val intent = Intent(this, com.hafij.islamicalarm.books.IslamicLibraryActivity::class.java)
        startActivity(intent)
    }

    private fun openAudioQuranActivity() {
        val intent = Intent(this, com.hafij.islamicalarm.audio.AudioQuranActivity::class.java)
        startActivity(intent)
    }

    private fun openAzanAudioActivity() {
        val intent = Intent(this, com.hafij.islamicalarm.audio.AzanAudioActivity::class.java)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_audio_quran -> {
                openAudioQuranActivity()
                true
            }
            R.id.action_azan -> {
                openAzanAudioActivity()
                true
            }
            R.id.action_books -> {
                openLibraryActivity()
                true
            }
            R.id.action_quran -> {
                openQuranActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
