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
import com.hafij.islamicalarm.permissions.PermissionHelper
import com.hafij.islamicalarm.amal.AmalTrackerActivity
import com.hafij.islamicalarm.data.AlarmItem
import com.hafij.islamicalarm.data.AlarmStore
import com.hafij.islamicalarm.databinding.ActivityMainBinding
import com.hafij.islamicalarm.databinding.DialogAddAlarmBinding
import com.hafij.islamicalarm.databinding.DialogLocationPickerBinding
import com.hafij.islamicalarm.names.AllahNamesActivity
import com.hafij.islamicalarm.prayertimes.BanglaCalendarHelper
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
    private var lastScheduleKey: String? = null
    private var countdownTimer: CountDownTimer? = null
    private var pendingPermissionChainNext: (() -> Unit)? = null

    // Start-up (in-app) permission wizard & Alarm (settings) permission wizard
    private val skippedHomePermSteps = mutableSetOf<String>()
    private val skippedAlarmPermSteps = mutableSetOf<String>()
    private var permDialog: androidx.appcompat.app.AlertDialog? = null
    private var autoDetectTried = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the wizard moves on in onResume */ }

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

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (com.hafij.islamicalarm.auto.AutoAlarmManager.isAutoModeEnabled(this)) {
                com.hafij.islamicalarm.auto.AutoAlarmManager.syncAutoMode(this, fromForeground = true)
            }
            Toast.makeText(
                this,
                "✅ আসর ও ইশার ওয়াক্তে আজান শনাক্তের জন্য মাইক্রোফোন অনুমতি চালু হয়েছে।",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                "মাইক্রোফোন অনুমতি ছাড়া আসর ও ইশার আজান শনাক্ত হবে না; শুধু যোহর, জুমা ও মাগরিব অটো চলবে।",
                Toast.LENGTH_LONG
            ).show()
        }
        refreshAutoAlarmUi()
    }

    private val enableGpsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(this, "✅ জিপিএস লোকেশন চালু হয়েছে! সময় সেট করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            autoDetectLocationAndSetPrayerTimes(showToast = true)
        } else {
            if (LocationHelper.isLocationEnabled(this)) {
                autoDetectLocationAndSetPrayerTimes(showToast = true)
            } else {
                showEnableGpsDialog()
            }
        }
    }

    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val method = try {
                CalculationMethod.valueOf(
                    data.getStringExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.RESULT_METHOD) ?: CalculationMethod.KARACHI_BD.name
                )
            } catch (e: Exception) {
                CalculationMethod.KARACHI_BD
            }
            val nameBn = data.getStringExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.RESULT_NAME_BN).orEmpty()
            val parentBn = data.getStringExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.RESULT_PARENT_BN).orEmpty()
            val picked = District(
                nameEn = data.getStringExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.RESULT_NAME_EN) ?: nameBn,
                nameBn = nameBn,
                divisionBn = parentBn,
                lat = data.getDoubleExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.RESULT_LAT, 0.0),
                lng = data.getDoubleExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.RESULT_LNG, 0.0),
                offsetMinutes = 0,
                timeZone = data.getDoubleExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.RESULT_TZ, 6.0),
                type = LocationType.CUSTOM_GPS,
                parentBn = parentBn,
                method = method
            )
            if (picked.lat != 0.0 && picked.lng != 0.0 && nameBn.isNotBlank()) {
                saveSelectedDistrict(picked)
                updatePrayerTimes()
                Toast.makeText(this, "✅ লোকেশন সেট হয়েছে: ${picked.displayName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            autoDetectTried = true
            Toast.makeText(this, "📡 লোকেশন অনুমতি পাওয়া গেছে। GPS চেক করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            promptTurnOnGpsAndDetect(showToast = true)
        } else {
            Toast.makeText(
                this,
                "লোকেশন পারমিশন ছাড়া ডিফল্ট (${selectedDistrict.nameBn}) সময়সূচী অনুযায়ী চলবে।",
                Toast.LENGTH_LONG
            ).show()
        }

        // Continue the rest of the permission chain only now that the location
        // system dialog has actually been answered (fixes multiple permission
        // dialogs popping up on top of each other at once).
        pendingPermissionChainNext?.invoke()
        pendingPermissionChainNext = null
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
        handleOpenTabIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.fragmentContainer.visibility == View.VISIBLE) {
                    val duas = supportFragmentManager.findFragmentByTag("duas")
                            as? com.hafij.islamicalarm.duas.DuasFragment
                    val quran = supportFragmentManager.findFragmentByTag("quran")
                            as? com.hafij.islamicalarm.quran.QuranFragment
                    val settings = supportFragmentManager.findFragmentByTag("settings")
                            as? com.hafij.islamicalarm.settings.SettingsFragment
                    val handledByDuas = duas != null && !duas.isHidden && duas.handleBack()
                    val handledByQuran = quran != null && !quran.isHidden && quran.handleBack()
                    val handledBySettings = settings != null && !settings.isHidden && settings.handleBack()
                    if (!handledByDuas && !handledByQuran && !handledBySettings) {
                        binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
                    }
                } else if (binding.layoutAlarmContainer.visibility == View.VISIBLE) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })


        // Keep Auto Silent active permanently while hidden from the homepage
        com.hafij.islamicalarm.silent.AutoSilentManager.setAutoSilentEnabled(this, true)
        binding.switchAutoSilent.isChecked = true

        loadAlarms()
        refreshOccasionNotificationsAndBadge()

        // Check & request runtime permissions on launch (Location, Overlay, Exact Alarm, Battery, Autostart, Phone)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenTabIntent(intent)
    }

    // Quran / Tasbih / Duas pages send the user back here with the tab to show
    private fun handleOpenTabIntent(intent: Intent?) {
        when (intent?.getStringExtra(BottomNavHelper.EXTRA_OPEN_TAB)) {
            BottomNavHelper.TAB_HOME -> binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
            BottomNavHelper.TAB_ALARM -> binding.bottomNavigation.selectedItemId = R.id.nav_alarm
            "quran" -> binding.bottomNavigation.selectedItemId = R.id.nav_quran
            "tasbih" -> binding.bottomNavigation.selectedItemId = R.id.nav_tasbih
            "duas" -> binding.bottomNavigation.selectedItemId = R.id.nav_duas
            "settings" -> binding.bottomNavigation.selectedItemId = R.id.nav_settings
        }
        intent?.removeExtra(BottomNavHelper.EXTRA_OPEN_TAB)
    }

    override fun onResume() {
        super.onResume()
        if (binding.layoutAlarmContainer.visibility == View.VISIBLE) {
            updateAlarmPermissionBanner()
            refreshAutoAlarmUi()
            runAlarmPermissionWizard()
        } else if (binding.layoutPrayerTimesContainer.visibility == View.VISIBLE) {
            runHomePermissionWizard()
        }
        updatePrayerTimes(force = true)
        loadAlarms()
        refreshOccasionNotificationsAndBadge()
        if (com.hafij.islamicalarm.auto.AutoAlarmManager.isAutoModeEnabled(this)) {
            com.hafij.islamicalarm.auto.AutoAlarmManager.syncAutoMode(this, fromForeground = true)
        }
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

    private fun saveSelectedDistrict(district: District, explicitUserOrGpsSet: Boolean = true) {
        selectedDistrict = district
        lastScheduleKey = null
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
            .apply {
                if (explicitUserOrGpsSet) {
                    putBoolean("has_user_set_location", true)
                }
            }
            .apply()
        com.hafij.islamicalarm.widget.IslamicAlarmWidget.refreshAllWidgets(this)
        if (com.hafij.islamicalarm.auto.AutoAlarmManager.isAutoModeEnabled(this)) {
            com.hafij.islamicalarm.auto.AutoAlarmManager.syncAutoMode(this)
        }
        refreshAutoAlarmUi()
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

    private fun promptTurnOnGpsAndDetect(showToast: Boolean = true) {
        if (!LocationHelper.hasLocationPermission(this)) {
            requestLocationPermissionDirect()
            return
        }

        LocationHelper.checkAndPromptEnableGps(
            activity = this,
            onGpsAlreadyEnabled = {
                autoDetectLocationAndSetPrayerTimes(showToast = showToast)
            },
            onResolutionRequired = { resolvable ->
                try {
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                        resolvable.resolution.intentSender
                    ).build()
                    enableGpsLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    showEnableGpsDialog()
                }
            },
            onGpsUnavailable = {
                showEnableGpsDialog()
            }
        )
    }

    private fun showEnableGpsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("📍 ফোনের GPS/লোকেশন চালু করুন")
            .setMessage("সঠিক নামাজের সময়সূচী স্বয়ংক্রিয়ভাবে সেট করার জন্য ফোনের লোকেশন (GPS) সেবা অন করা আবশ্যক।")
            .setPositiveButton("সেটিংস খুলুন") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("পরে করব", null)
            .show()
    }

    private fun setupBottomNavigation() {
        // Set default selection to Home: Prayer Times & Alarms
        binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
        showPrayerTimesScreen()

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_prayer_times -> {
                    showPrayerTimesScreen()
                    true
                }
                R.id.nav_alarm -> {
                    showAlarmScreen()
                    true
                }
                R.id.nav_quran -> {
                    showFragmentScreen("quran")
                    true
                }
                R.id.nav_tasbih -> {
                    showFragmentScreen("tasbih")
                    true
                }
                R.id.nav_duas -> {
                    showFragmentScreen("duas")
                    true
                }
                R.id.nav_settings -> {
                    showFragmentScreen("settings")
                    true
                }
                else -> false
            }
        }
    }

    private val fragmentScreenTags = listOf("quran", "tasbih", "duas", "settings")

    /** Shows Quran / Tasbih / Dua inside this Activity (no new window = no flicker). */
    private fun showFragmentScreen(tag: String) {
        dismissPermDialog()
        binding.layoutPrayerTimesContainer.visibility = View.GONE
        binding.layoutAlarmContainer.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE

        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        var target = fm.findFragmentByTag(tag)
        if (target == null) {
            target = when (tag) {
                "quran" -> com.hafij.islamicalarm.quran.QuranFragment()
                "tasbih" -> com.hafij.islamicalarm.tasbih.TasbihFragment()
                "settings" -> com.hafij.islamicalarm.settings.SettingsFragment()
                else -> com.hafij.islamicalarm.duas.DuasFragment()
            }
            tx.add(R.id.fragmentContainer, target, tag)
        }
        for (t in fragmentScreenTags) {
            if (t != tag) fm.findFragmentByTag(t)?.let { tx.hide(it) }
        }
        tx.show(target)
        tx.commitAllowingStateLoss()
    }

    private fun hideFragmentScreens() {
        binding.fragmentContainer.visibility = View.GONE
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        for (t in fragmentScreenTags) fm.findFragmentByTag(t)?.let { tx.hide(it) }
        tx.commitAllowingStateLoss()
    }

    /** Called by the Quran / Tasbih / Dua pages' back arrow. */
    fun goHome() {
        binding.bottomNavigation.selectedItemId = R.id.nav_prayer_times
    }

    private fun showAlarmScreen() {
        dismissPermDialog()
        hideFragmentScreens()
        binding.layoutPrayerTimesContainer.visibility = View.GONE
        binding.layoutAlarmContainer.visibility = View.VISIBLE
        skippedAlarmPermSteps.clear()
        updateAlarmPermissionBanner()
        refreshAutoAlarmUi()
        binding.root.post {
            if (isFinishing || isDestroyed) return@post
            if (binding.layoutAlarmContainer.visibility != View.VISIBLE) return@post
            updatePrayerTimes()
            loadAlarms()
            runAlarmPermissionWizard()
        }
    }

    private fun formattedLocationButtonText(): String {
        val cleanName = selectedDistrict.displayName.removePrefix("📍 ").trim()
        return "$cleanName ▾"
    }

    private fun updateLocationHeaderUi() {
        binding.btnSelectDistrict.text = formattedLocationButtonText()
        val hasSet = prefs.getBoolean("has_user_set_location", false)
        val locationReady = LocationHelper.hasLocationPermission(this) &&
            LocationHelper.isLocationEnabled(this) &&
            hasSet
        binding.tvLocationPromptHint.visibility = if (locationReady) View.GONE else View.VISIBLE
    }

    private fun showPrayerTimesScreen() {
        dismissPermDialog()
        hideFragmentScreens()
        binding.layoutAlarmContainer.visibility = View.GONE
        binding.layoutPrayerTimesContainer.visibility = View.VISIBLE
        binding.tvHeaderTitle.text = "নামাজের সময়সূচী"
        updateLocationHeaderUi()
        binding.root.post {
            if (isFinishing || isDestroyed) return@post
            if (binding.layoutPrayerTimesContainer.visibility != View.VISIBLE) return@post
            updatePrayerTimes()
            runHomePermissionWizard()
        }
    }

    private fun setupPrayerTimesHub() {

        binding.btnGrantMissingAlarmPerms.setOnClickListener {
            skippedAlarmPermSteps.clear()
            runAlarmPermissionWizard()
        }

        refreshAutoAlarmUi()
        binding.cardAutoAlarm.setOnClickListener {
            binding.switchAutoAlarm.toggle()
        }

        // District / Location Selector (dynamic wrap_content width capped cleanly before GPS button)
        binding.layoutHomeLocationRow.addOnLayoutChangeListener { row, _, _, _, _, _, _, _, _ ->
            val rowWidth = row.width
            val gpsWidth = binding.btnQuickGpsLocation.width
            if (rowWidth > 0 && gpsWidth > 0) {
                val marginBuffer = (8 * resources.displayMetrics.density).toInt()
                val minWidthPx = (108 * resources.displayMetrics.density).toInt()
                val availableMax = (rowWidth - gpsWidth - marginBuffer).coerceAtLeast(minWidthPx)
                if (binding.btnSelectDistrict.maxWidth != availableMax) {
                    binding.btnSelectDistrict.maxWidth = availableMax
                }
            }
        }
        binding.btnSelectDistrict.setOnClickListener {
            showDistrictSelectionDialog()
        }
        binding.tvLocationPromptHint.setOnClickListener {
            promptTurnOnGpsAndDetect(showToast = true)
        }

        // Quick GPS Button
        binding.btnQuickGpsLocation.setOnClickListener {
            promptTurnOnGpsAndDetect(showToast = true)
        }

        // Top-right Special Islamic Day Notifications Bell
        binding.btnHomeNotifications.setOnClickListener {
            startActivity(
                Intent(this, com.hafij.islamicalarm.notifications.IslamicNotificationsActivity::class.java)
            )
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

        // Waqt Alarm toggle switches
        syncWaqtAlarmSwitches()
    }

    private data class WaqtSwitchSpec(
        val switchView: androidx.appcompat.widget.SwitchCompat,
        val shortNameBn: String,
        val label: String,
        val timeProvider: (PrayerSchedule) -> Pair<Int, Int>
    )

    private fun waqtSwitchSpecs(): List<WaqtSwitchSpec> = listOf(
        WaqtSwitchSpec(binding.btnAlarmFajr, "ফজর", "ফজর সালাত") {
            it.fajrCal.get(Calendar.HOUR_OF_DAY) to it.fajrCal.get(Calendar.MINUTE)
        },
        WaqtSwitchSpec(binding.btnAlarmDhuhr, "যোহর", "যোহর সালাত") {
            it.dhuhrCal.get(Calendar.HOUR_OF_DAY) to it.dhuhrCal.get(Calendar.MINUTE)
        },
        WaqtSwitchSpec(binding.btnAlarmAsr, "আসর", "আসর সালাত") {
            it.asrCal.get(Calendar.HOUR_OF_DAY) to it.asrCal.get(Calendar.MINUTE)
        },
        WaqtSwitchSpec(binding.btnAlarmMaghrib, "মাগরিব", "মাগরিব সালাত ও ইফতার") {
            it.maghribCal.get(Calendar.HOUR_OF_DAY) to it.maghribCal.get(Calendar.MINUTE)
        },
        WaqtSwitchSpec(binding.btnAlarmIsha, "ইশা", "ইশা ও তারাবিহ/বিতর") {
            it.ishaCal.get(Calendar.HOUR_OF_DAY) to it.ishaCal.get(Calendar.MINUTE)
        }
    )

    private fun syncWaqtAlarmSwitches() {
        if (!::binding.isInitialized || !::alarmStore.isInitialized) return
        val alarms = alarmStore.getAlarms()
        for (spec in waqtSwitchSpecs()) {
            val isOn = alarms.any { it.label == spec.label && it.isEnabled }
            spec.switchView.setOnCheckedChangeListener(null)
            spec.switchView.isChecked = isOn
            spec.switchView.setOnCheckedChangeListener { _, checked ->
                onWaqtAlarmSwitchToggled(spec, checked)
            }
        }
    }

    private fun onWaqtAlarmSwitchToggled(spec: WaqtSwitchSpec, checked: Boolean) {
        if (checked) {
            if (!ensureAlarmPermissionsBeforeSetting()) {
                syncWaqtAlarmSwitches()
                return
            }
            val existing = alarmStore.getAlarms().firstOrNull { it.label == spec.label }
            if (existing != null) {
                val updated = existing.copy(isEnabled = true)
                alarmStore.updateAlarm(updated)
                val triggerMillis = AlarmScheduler.scheduleAlarm(this, updated)
                Toast.makeText(
                    this,
                    AlarmScheduler.getFormattedNextRingTime(this, triggerMillis),
                    Toast.LENGTH_LONG
                ).show()
                loadAlarms()
            } else {
                val schedule = currentSchedule
                if (schedule != null) {
                    val (hour, minute) = spec.timeProvider(schedule)
                    showQuickAlarmDialog(hour, minute, spec.label)
                } else {
                    syncWaqtAlarmSwitches()
                }
            }
        } else {
            val matching = alarmStore.getAlarms().filter { it.label == spec.label && it.isEnabled }
            for (alarm in matching) {
                AlarmScheduler.cancelAlarm(this, alarm.id)
                alarmStore.updateAlarm(alarm.copy(isEnabled = false))
            }
            if (matching.isNotEmpty()) {
                Toast.makeText(this, "${spec.shortNameBn} এলার্ম বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
            loadAlarms()
        }
    }

    private fun refreshOccasionNotificationsAndBadge() {
        if (!::binding.isInitialized) return
        com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.scheduleDailyCheck(this)
        com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.evaluateAndNotifyToday(this)
        updateHomeNotificationBadgeUi()
        lifecycleScope.launch {
            val inserted = com.hafij.islamicalarm.notifications.IslamicOccasionNotifier
                .evaluateAndNotifyDailyHadith(this@MainActivity, allowNetwork = true)
            if (inserted != null && !isFinishing && !isDestroyed) {
                updateHomeNotificationBadgeUi()
            }
        }
    }

    private fun updateHomeNotificationBadgeUi() {
        if (!::binding.isInitialized) return
        val unreadCount = com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.getUnreadCount(this)
        if (unreadCount > 0) {
            binding.tvHomeNotificationBadge.visibility = View.VISIBLE
            binding.tvHomeNotificationBadge.text =
                com.hafij.islamicalarm.notifications.IslamicOccasionNotifier.formatUnreadBadge(unreadCount)
        } else {
            binding.tvHomeNotificationBadge.visibility = View.GONE
        }
    }

    private fun showDistrictSelectionDialog() {
        val dialogBinding = DialogLocationPickerBinding.inflate(LayoutInflater.from(this))

        var currentFilteredList = LocationData.allLocations

        dialogBinding.rvLocationList.layoutManager = LinearLayoutManager(this)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        // Step-by-step (cascade) state, only used while the "বিভাগ" (Division) tab is active
        var drillDivision: District? = null
        var drillDistrict: District? = null

        lateinit var updatedAdapter: LocationAdapter

        fun currentSearchQuery(): String =
            dialogBinding.etSearchLocation.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        fun applySearchQuery(list: List<District>): List<District> {
            val q = currentSearchQuery()
            if (q.isBlank()) return list
            return list.filter {
                it.nameBn.lowercase(Locale.ROOT).contains(q) ||
                it.nameEn.lowercase(Locale.ROOT).contains(q) ||
                it.divisionBn.lowercase(Locale.ROOT).contains(q) ||
                it.parentBn.lowercase(Locale.ROOT).contains(q)
            }
        }

        // Flat search filter (used by "সবগুলো" / জেলা / থানা / গ্লোবাল tabs)
        fun filterLocations() {
            val selectedChipId = dialogBinding.chipGroupLocationFilter.checkedChipId
            val baseList = when (selectedChipId) {
                R.id.chipDistricts -> LocationData.districts
                R.id.chipThanas -> LocationData.thanas
                R.id.chipDivisions -> LocationData.divisions
                R.id.chipGlobal -> LocationData.globalCities
                else -> LocationData.allLocations
            }
            updatedAdapter.updateList(applySearchQuery(baseList))
        }

        // Renders either the step-by-step cascade (বিভাগ tab) or the flat search list (other tabs)
        fun refreshLocationList() {
            val selectedChipId = dialogBinding.chipGroupLocationFilter.checkedChipId
            if (selectedChipId == R.id.chipDivisions) {
                when {
                    drillDistrict != null -> {
                        val district = drillDistrict!!
                        val thanaList = listOf(district) +
                            LocationData.thanas.filter { it.parentBn == district.nameBn }
                        dialogBinding.tvLocationBreadcrumb.visibility = View.VISIBLE
                        dialogBinding.tvLocationBreadcrumb.text =
                            "← ${district.nameBn} জেলার থানা/উপজেলা (ফিরে যেতে ট্যাপ করুন)"
                        updatedAdapter.updateList(applySearchQuery(thanaList))
                    }
                    drillDivision != null -> {
                        val division = drillDivision!!
                        val districtList = LocationData.districts.filter { it.divisionBn == division.divisionBn }
                        dialogBinding.tvLocationBreadcrumb.visibility = View.VISIBLE
                        dialogBinding.tvLocationBreadcrumb.text =
                            "← ${division.nameBn} বিভাগের জেলা (ফিরে যেতে ট্যাপ করুন)"
                        updatedAdapter.updateList(applySearchQuery(districtList))
                    }
                    else -> {
                        dialogBinding.tvLocationBreadcrumb.visibility = View.GONE
                        updatedAdapter.updateList(applySearchQuery(LocationData.divisions))
                    }
                }
            } else {
                dialogBinding.tvLocationBreadcrumb.visibility = View.GONE
                filterLocations()
            }
        }

        val itemClickListener: (District) -> Unit = { chosen ->
            val selectedChipId = dialogBinding.chipGroupLocationFilter.checkedChipId
            if (selectedChipId == R.id.chipDivisions) {
                when {
                    drillDistrict != null -> {
                        // User tapped a Thana (or the pinned "this district" option) - finalize
                        saveSelectedDistrict(chosen)
                        updatePrayerTimes()
                        dialog.dismiss()
                        Toast.makeText(this, "${chosen.displayName} এর সময়সূচী সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                    drillDivision != null -> {
                        // User tapped a District within the chosen Division
                        val hasThanas = LocationData.thanas.any { it.parentBn == chosen.nameBn }
                        if (hasThanas) {
                            drillDistrict = chosen
                            refreshLocationList()
                        } else {
                            saveSelectedDistrict(chosen)
                            updatePrayerTimes()
                            dialog.dismiss()
                            Toast.makeText(this, "${chosen.displayName} এর সময়সূচী সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        // User tapped a Division - drill down into its Districts
                        drillDivision = chosen
                        refreshLocationList()
                    }
                }
            } else {
                saveSelectedDistrict(chosen)
                updatePrayerTimes()
                dialog.dismiss()
                Toast.makeText(this, "${chosen.displayName} এর সময়সূচী সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }

        updatedAdapter = LocationAdapter(currentFilteredList, itemClickListener)
        dialogBinding.rvLocationList.adapter = updatedAdapter

        dialogBinding.btnCloseLocationDialog.setOnClickListener {
            dialog.dismiss()
        }

        // Auto GPS Detect Button
        dialogBinding.btnDetectGpsLocation.setOnClickListener {
            dialog.dismiss()
            promptTurnOnGpsAndDetect(showToast = true)
        }

        // Pick exact place on the map
        dialogBinding.btnPickOnMap.setOnClickListener {
            dialog.dismiss()
            val mapIntent = Intent(this, com.hafij.islamicalarm.prayertimes.MapPickerActivity::class.java)
            if (prefs.getBoolean("has_user_set_location", false)) {
                mapIntent.putExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.EXTRA_LAT, selectedDistrict.lat)
                mapIntent.putExtra(com.hafij.islamicalarm.prayertimes.MapPickerActivity.EXTRA_LNG, selectedDistrict.lng)
            }
            mapPickerLauncher.launch(mapIntent)
        }

        dialogBinding.tvLocationBreadcrumb.setOnClickListener {
            when {
                drillDistrict != null -> {
                    drillDistrict = null
                    refreshLocationList()
                }
                drillDivision != null -> {
                    drillDivision = null
                    refreshLocationList()
                }
            }
        }

        dialogBinding.etSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshLocationList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.chipGroupLocationFilter.setOnCheckedStateChangeListener { _, _ ->
            drillDivision = null
            drillDistrict = null
            refreshLocationList()
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
                    Toast.makeText(this@MainActivity, "${onlineResults.size}টি অনলাইন ফলাফল পাওয়া গেছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "'$query' এর জন্য অনলাইন তথ্য পাওয়া যায়নি। অফলাইন তালিকা ব্যবহার করুন।", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    private fun updatePrayerTimes(force: Boolean = false) {
        val now = Calendar.getInstance()
        val key = "${selectedDistrict.nameBn}_${now.get(Calendar.YEAR)}_${now.get(Calendar.DAY_OF_YEAR)}"
        val schedule = if (!force && key == lastScheduleKey && currentSchedule != null) {
            currentSchedule!!
        } else {
            lastScheduleKey = key
            PrayerTimeCalculator.calculate(now, selectedDistrict).also { sch ->
                currentSchedule = sch
                updateLocationHeaderUi()

                // Hijri + Gregorian + Bangla date, all three together in the compact badge
                val hijriDate = HijriCalendarHelper.getHijriDate(now)
                val gregorianBn = BanglaCalendarHelper.formatGregorianBn(now)
                val banglaDate = BanglaCalendarHelper.getBanglaDate(now)
                binding.tvMainHijriDate.text =
                    "${hijriDate.formattedBn}\n$gregorianBn, ${banglaDate.formattedBn}"

                // Times
                binding.tvFajrTime.text = "শুরু: ভোর ${sch.fajr} — সূর্যোদয়: ভোর ${sch.sunrise}"
                binding.tvDhuhrTime.text = "শুরু: দুপুর ${sch.dhuhr} — আসর শুরু পর্যন্ত"
                binding.tvAsrTime.text = "শুরু: বিকাল ${sch.asr} — সূর্যাস্ত: সন্ধ্যা ${sch.sunset}"
                binding.tvMaghribTime.text = "শুরু: সন্ধ্যা ${sch.maghrib} (ইফতার) — ইশা শুরু পর্যন্ত"
                binding.tvIshaTime.text = "শুরু: রাত ${sch.isha} — ফজর শুরু পর্যন্ত"

                // Alarm screen times
                binding.tvAlarmFajrTime.text = "শুরু: ভোর ${sch.fajr}"
                binding.tvAlarmDhuhrTime.text = "শুরু: দুপুর ${sch.dhuhr}"
                binding.tvAlarmAsrTime.text = "শুরু: বিকাল ${sch.asr}"
                binding.tvAlarmMaghribTime.text = "শুরু: সন্ধ্যা ${sch.maghrib}"
                binding.tvAlarmIshaTime.text = "শুরু: রাত ${sch.isha}"

                // Sahri: starts at midnight (last part of the night), ends 5 minutes before Fajr
                binding.tvSahriTimes.text = "শুরু: ${sch.midnight} — শেষ: ${sch.sehriEnd}"

                // Iftar: starts at Maghrib, the waqt runs until Isha begins
                binding.tvIftarTimes.text = "শুরু: ${sch.maghrib} — শেষ: ${sch.isha}"
            }
        }

        updateLocationHeaderUi()

        binding.tvCurrentWaqtStatus.text = PrayerTimeCalculator.getCurrentWaqtName(now, schedule)
        updateHeaderBackground(now, schedule)

        if (force || countdownTimer == null) {
            startPrayerCountdownTicker()
        }
    }

    private fun onAutoAlarmSwitchChanged(checked: Boolean) {
        if (!checked) {
            com.hafij.islamicalarm.auto.AutoAlarmManager.setAutoModeEnabled(this, false)
            refreshAutoAlarmUi()
            Toast.makeText(this, "অটো এলার্ম বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
            return
        }

        // Keep the switch visually OFF until user reads the notice and taps "ওকে"
        refreshAutoAlarmUi()
        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 অটো মোড — কীভাবে কাজ করে")
            .setMessage(
                "অটো মোড চালু করলে যা হবে:\n" +
                    "• প্রতিদিন যোহর (দুপুর ১:২০), জুমার দিন (১২:৪৫) ও আপনার এলাকার মাগরিবের সময় ২০ মিনিটের অটো এলার্ম আপনা-আপনি সেট হয়ে চালু হবে।\n" +
                    "• আসর ও ইশার ওয়াক্তে আজান শোনা মাত্রই ১৫ মিনিটের জন্য এলার্ম সেট হয়ে চালু হবে (এর জন্য মাইক্রোফোন অনুমতি প্রয়োজন)।\n\n" +
                    "চালু করতে 'ওকে' চাপুন।"
            )
            .setPositiveButton("ওকে") { _, _ ->
                if (!ensureAlarmPermissionsBeforeSetting()) {
                    refreshAutoAlarmUi()
                    return@setPositiveButton
                }
                com.hafij.islamicalarm.auto.AutoAlarmManager.setAutoModeEnabled(this, true)
                refreshAutoAlarmUi()
                if (!com.hafij.islamicalarm.auto.AutoAlarmManager.hasRecordAudioPermission(this)) {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    Toast.makeText(
                        this,
                        "✅ অটো মোড চালু: যোহর/জুমা/মাগরিব (২০ মি.) ও আসর/ইশা আজান শনাক্ত (১৫ মি.) সক্রিয়।",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("বাতিল") { _, _ -> refreshAutoAlarmUi() }
            .setOnCancelListener { refreshAutoAlarmUi() }
            .show()
    }

    private fun refreshAutoAlarmUi() {
        if (!::binding.isInitialized) return
        val enabled = com.hafij.islamicalarm.auto.AutoAlarmManager.isAutoModeEnabled(this)
        binding.switchAutoAlarm.setOnCheckedChangeListener(null)
        binding.switchAutoAlarm.isChecked = enabled
        binding.switchAutoAlarm.setOnCheckedChangeListener { _, checked ->
            onAutoAlarmSwitchChanged(checked)
        }
        binding.tvAutoAlarmStatus.text =
            com.hafij.islamicalarm.auto.AutoAlarmManager.buildStatusSummary(this)
    }

    /**
     * Swaps the home header's crisp scenic mosque illustration to match
     * all 5 waqt periods: Fajr dawn, Day/Dhuhr, Asr golden afternoon,
     * Maghrib twilight, and Isha/Tahajjud night.
     */
    private fun updateHeaderBackground(now: Calendar, schedule: com.hafij.islamicalarm.prayertimes.PrayerSchedule) {
        val artRes = resolveHeaderImageRes(now, schedule)
        if (binding.ivHeaderTimeArt.tag != artRes) {
            binding.ivHeaderTimeArt.tag = artRes
            binding.ivHeaderTimeArt.setImageResource(artRes)
        }
    }

    internal fun resolveHeaderImageRes(
        now: Calendar,
        schedule: com.hafij.islamicalarm.prayertimes.PrayerSchedule
    ): Int {
        val nowMillis = now.timeInMillis
        return when {
            nowMillis in schedule.fajrCal.timeInMillis until schedule.sunriseCal.timeInMillis ->
                R.drawable.img_header_fajr
            nowMillis in schedule.sunriseCal.timeInMillis until schedule.asrCal.timeInMillis ->
                R.drawable.img_header_day
            nowMillis in schedule.asrCal.timeInMillis until schedule.maghribCal.timeInMillis ->
                R.drawable.img_header_asr
            nowMillis in schedule.maghribCal.timeInMillis until schedule.ishaCal.timeInMillis ->
                R.drawable.img_header_maghrib
            else ->
                R.drawable.img_header_night
        }
    }

    private fun startPrayerCountdownTicker() {
        countdownTimer?.cancel()
        val schedule = currentSchedule ?: return
        val now = Calendar.getInstance()
        val nextPrayer = PrayerTimeCalculator.getNextPrayer(now, schedule)

        binding.tvNextPrayerTitle.text = "পরবর্তী নামাজ: ${nextPrayer.prayerNameBn} (${nextPrayer.prayerTimeStr})"
        binding.tvNextPrayerSunTimes.text =
            "🌅 সূর্যোদয়: ${schedule.sunrise}   🌇 সূর্যাস্ত: ${schedule.sunset}"

        countdownTimer = object : CountDownTimer(nextPrayer.remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvNextPrayerCountdown.text = "বাকি " + PrayerTimeCalculator.formatCountdown(millisUntilFinished)
                updateSahriIftarCountdown(schedule)
            }

            override fun onFinish() {
                updatePrayerTimes()
            }
        }.start()

        updateSahriIftarCountdown(schedule)
    }

    private fun updateSahriIftarCountdown(schedule: com.hafij.islamicalarm.prayertimes.PrayerSchedule) {
        val nowMillis = Calendar.getInstance().timeInMillis

        // Sahri countdown: time remaining until Sahri ends (Fajr - 5 min). If already past,
        // count down to tomorrow's Sahri end instead.
        val sahriEndMillis = if (nowMillis < schedule.sehriCal.timeInMillis) {
            schedule.sehriCal.timeInMillis
        } else {
            (schedule.sehriCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }
        binding.tvSahriCountdown.text = "শেষ হতে বাকি " + PrayerTimeCalculator.formatCountdown(sahriEndMillis - nowMillis)

        // Iftar countdown: time remaining until Iftar starts (Maghrib). If already past,
        // count down to tomorrow's Iftar instead.
        val iftarStartMillis = if (nowMillis < schedule.maghribCal.timeInMillis) {
            schedule.maghribCal.timeInMillis
        } else {
            (schedule.maghribCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }
        binding.tvIftarCountdown.text = "শুরু হতে বাকি " + PrayerTimeCalculator.formatCountdown(iftarStartMillis - nowMillis)
    }

    private fun showQuickAlarmDialog(hour: Int, minute: Int, defaultLabel: String) {
        if (!ensureAlarmPermissionsBeforeSetting()) return
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
                if (isEnabled && !ensureAlarmPermissionsBeforeSetting()) {
                    loadAlarms()
                    return@AlarmAdapter
                }
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
                if (alarm.isEnabled && !ensureAlarmPermissionsBeforeSetting()) {
                    return@AlarmAdapter
                }
                showAddOrEditAlarmDialog(alarm)
            },
            onDeleteAlarm = { alarm ->
                showDeleteConfirmDialog(alarm)
            },
            onTestAlarm = { alarm ->
                Toast.makeText(this, "🔔 সালাত এলার্ম লক স্ক্রিন চালু হচ্ছে...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LockScreenActivity::class.java).apply {
                    putExtra("ALARM_ID", alarm.id)
                    putExtra("LOCK_DURATION_MINUTES", if (alarm.lockDurationMinutes > 0) alarm.lockDurationMinutes else 1)
                    putExtra("LABEL", alarm.label.ifBlank { "নামাজ লক টেস্ট" })
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
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
        syncWaqtAlarmSwitches()
    }

    private fun showAddOrEditAlarmDialog(existingAlarm: AlarmItem?) {
        // existingAlarm can also be an unsaved template (quick waqt alarm buttons).
        // Only treat it as "editing" if it is really already stored; otherwise it must be ADDED,
        // or updateAlarm() silently saves nothing and the alarm never fires.
        val isEditing = existingAlarm != null && alarmStore.getAlarm(existingAlarm.id) != null
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

        if (existingAlarm != null) {
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
                    lockLevel = "HARD",
                    isRepeatDaily = isRepeatDaily,
                    isEnabled = true
                )
            } else {
                AlarmItem(
                    hour = selectedHour,
                    minute = selectedMinute,
                    label = label,
                    lockDurationMinutes = lockDuration,
                    lockLevel = "HARD",
                    isRepeatDaily = isRepeatDaily,
                    isEnabled = true
                )
            }

            val willBeEnabled = if (isEditing) existingAlarm!!.isEnabled else true
            if (willBeEnabled && !ensureAlarmPermissionsBeforeSetting()) {
                return@setOnClickListener
            }

            if (isEditing) {
                alarmStore.updateAlarm(alarm)
            } else {
                alarmStore.addAlarm(alarm)
            }

            if (alarm.isEnabled) {
                val triggerMillis = AlarmScheduler.scheduleAlarm(this@MainActivity, alarm)
                val toastMsg = AlarmScheduler.getFormattedNextRingTime(this@MainActivity, triggerMillis)
                Toast.makeText(this@MainActivity, toastMsg, Toast.LENGTH_LONG).show()
            }

            loadAlarms()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            syncWaqtAlarmSwitches()
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

    // ------------------------------------------------------------------
    // Start-up permission wizard: asks every permission one by one.
    // ------------------------------------------------------------------

    private fun maybeAutoDetectLocation() {
        if (autoDetectTried) return
        autoDetectTried = true
        if (prefs.getString("selected_district_bn", null) == null) {
            if (LocationHelper.isLocationEnabled(this)) {
                autoDetectLocationAndSetPrayerTimes(showToast = false)
            } else {
                promptTurnOnGpsAndDetect(showToast = false)
            }
        }
    }

    private fun dismissPermDialog() {
        if (permDialog?.isShowing == true) {
            permDialog?.dismiss()
        }
        permDialog = null
    }

    private fun updateAlarmPermissionBanner() {
        val missing = PermissionHelper.missingAlarmSteps(this)
        if (missing.isEmpty()) {
            binding.cardAlarmPermissionBanner.visibility = View.GONE
        } else {
            binding.cardAlarmPermissionBanner.visibility = View.VISIBLE
            binding.tvAlarmPermBannerTitle.text =
                "⚠️ এলার্ম সেট করতে সবগুলো পারমিশন চালু করুন (${missing.size}টি বাকি)"
            binding.tvAlarmPermBannerMissing.text =
                "বাকি আছে: " + missing.joinToString(", ") { it.title }
        }
    }

    private fun ensureAlarmPermissionsBeforeSetting(): Boolean {
        val missing = PermissionHelper.missingAlarmSteps(this)
        if (missing.isEmpty()) return true
        updateAlarmPermissionBanner()
        Toast.makeText(
            this,
            "একটি পারমিশনও বাদ থাকলে এলার্ম সেট করা যাবে না (আরও ${missing.size}টি বাকি)",
            Toast.LENGTH_LONG
        ).show()
        skippedAlarmPermSteps.clear()
        runAlarmPermissionWizard()
        return false
    }

    private fun buildPermissionStepDialogView(step: com.hafij.islamicalarm.permissions.PermStep): View {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_step, null)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvPermDialogMessage)
        val cardGuide = dialogView.findViewById<View>(R.id.cardPermDialogGuide)
        val ivGuide = dialogView.findViewById<android.widget.ImageView>(R.id.ivPermDialogGuide)
        val tvHint = dialogView.findViewById<android.widget.TextView>(R.id.tvPermDialogGuideHint)

        tvMessage.text = step.why + "\n\n" + step.howTo
        val guideRes = PermissionHelper.guideImageResFor(step.id)
        if (guideRes != null) {
            cardGuide.visibility = View.VISIBLE
            ivGuide.setImageResource(guideRes)
            val hint = PermissionHelper.guideHintFor(step.id)
            tvHint.text = if (!hint.isNullOrBlank()) {
                "$hint\n🔍 বড় করে দেখতে ছবিতে ট্যাপ করুন"
            } else {
                "🖼️ ছবি বড় করে দেখতে ট্যাপ করুন 🔍"
            }
            cardGuide.setOnClickListener {
                com.hafij.islamicalarm.permissions.PermissionImageZoomDialog.show(this, step, guideRes)
            }
        } else {
            cardGuide.visibility = View.GONE
        }
        return dialogView
    }

    private fun runHomePermissionWizard() {
        if (isFinishing || isDestroyed) return
        if (permDialog?.isShowing == true) return
        if (binding.layoutPrayerTimesContainer.visibility != View.VISIBLE) return

        val steps = PermissionHelper.homeSteps(this)

        if (PermissionHelper.isGranted(this, PermissionHelper.ID_LOCATION)) {
            maybeAutoDetectLocation()
        }

        val next = steps.firstOrNull {
            it.id !in skippedHomePermSteps && !PermissionHelper.isGranted(this, it.id)
        } ?: return

        val number = steps.indexOf(next) + 1
        permDialog = MaterialAlertDialogBuilder(this)
            .setTitle("অনুমতি $number/${steps.size}: ${next.title}")
            .setView(buildPermissionStepDialogView(next))
            .setPositiveButton("অনুমতি দিন") { _, _ ->
                skippedHomePermSteps.add(next.id)
                requestPermissionStep(next.id)
            }
            .setNegativeButton("পরে করবো") { _, _ ->
                skippedHomePermSteps.add(next.id)
                binding.root.post { runHomePermissionWizard() }
            }
            .setCancelable(false)
            .show()
    }

    private fun runAlarmPermissionWizard() {
        if (isFinishing || isDestroyed) return
        if (permDialog?.isShowing == true) return
        if (binding.layoutAlarmContainer.visibility != View.VISIBLE) return

        val steps = PermissionHelper.alarmSteps(this)
        val next = steps.firstOrNull {
            it.id !in skippedAlarmPermSteps && !PermissionHelper.isGranted(this, it.id)
        } ?: return

        val number = steps.indexOf(next) + 1
        permDialog = MaterialAlertDialogBuilder(this)
            .setTitle("এলার্ম অনুমতি $number/${steps.size}: ${next.title}")
            .setView(buildPermissionStepDialogView(next))
            .setPositiveButton("অনুমতি দিন") { _, _ ->
                skippedAlarmPermSteps.add(next.id)
                if (next.id == PermissionHelper.ID_AUTOSTART) {
                    PermissionHelper.markAutostartDone(this)
                }
                requestPermissionStep(next.id)
            }
            .setNegativeButton("পরে করবো") { _, _ ->
                skippedAlarmPermSteps.add(next.id)
                updateAlarmPermissionBanner()
                binding.root.post { runAlarmPermissionWizard() }
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissionStep(id: String) {
        when (id) {
            PermissionHelper.ID_LOCATION -> requestLocationPermissionDirect()
            PermissionHelper.ID_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionHelper.openSettings(this, id)
                }
            }
            PermissionHelper.ID_PHONE ->
                phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            else -> PermissionHelper.openSettings(this, id)
        }
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
                        pendingPermissionChainNext = onNext
                        requestLocationPermissionDirect()
                    }
                    .setNegativeButton("পরে দিব") { _, _ ->
                        onNext()
                    }
                    .setCancelable(false)
                    .show()
                return
            }
        } else {
            // Already has permission. Only auto-detect via GPS if no district has
            // EVER been saved yet (true first run) — otherwise respect whatever
            // location is already saved and do not silently overwrite it just
            // because the app was reopened. Re-detecting here on every launch was
            // the cause of the saved location silently changing after a day or two.
            val hasSavedDistrict = prefs.getString("selected_district_bn", null) != null
            if (!hasSavedDistrict) {
                if (LocationHelper.isLocationEnabled(this)) {
                    autoDetectLocationAndSetPrayerTimes(showToast = false)
                } else {
                    promptTurnOnGpsAndDetect(showToast = false)
                }
            }
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

    private fun openLibraryActivity() {
        val intent = Intent(this, com.hafij.islamicalarm.books.IslamicLibraryActivity::class.java)
        startActivity(intent)
    }

}
