package com.hafij.islamicalarm.prayertimes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hafij.islamicalarm.databinding.ActivityMapPickerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import java.io.File
import java.util.Locale

/**
 * Full-screen OpenStreetMap picker with unlimited multi-source place search (offline catalog +
 * device Geocoder + Photon + Open-Meteo), tap-to-place pin, zoom controls, and GPS recenter.
 */
class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var requestId = 0
    private var resolved: District? = null
    private var searchJob: Job? = null
    private var skipNextLookupFromSearchPick = false
    private lateinit var searchAdapter: LocationAdapter

    private val lookupRunnable = Runnable { resolveCenter() }
    private val debouncedSearchRunnable = Runnable {
        val q = binding.etMapSearch.text?.toString()?.trim().orEmpty()
        if (q.length >= 2) {
            performPlaceSearch(q)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            moveToMyLocation()
        } else {
            Toast.makeText(this, "লোকেশন পারমিশন ছাড়া বর্তমান অবস্থান দেখানো যাবে না", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = "IslamicAlarm/1.0 ($packageName; android-app)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hasStart = intent.hasExtra(EXTRA_LAT) && intent.hasExtra(EXTRA_LNG)
        val startLat = intent.getDoubleExtra(EXTRA_LAT, 23.8103)
        val startLng = intent.getDoubleExtra(EXTRA_LNG, 90.4125)

        setupMap(startLat, startLng, hasStart)
        setupSearch()

        binding.btnMapBack.setOnClickListener { finish() }
        binding.btnMyLocation.setOnClickListener { moveToMyLocation() }
        binding.btnZoomIn.setOnClickListener { binding.mapView.controller.zoomIn() }
        binding.btnZoomOut.setOnClickListener { binding.mapView.controller.zoomOut() }
        binding.btnConfirmPlace.setOnClickListener { confirm() }

        scheduleLookup()
    }

    private fun setupMap(startLat: Double, startLng: Double, hasStart: Boolean) {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(if (hasStart) 17.0 else 7.0)
            controller.setCenter(GeoPoint(startLat, startLng))

            // Tapping anywhere on the map moves the pin there & dismisses search dropdown
            overlays.add(
                0,
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        hideSearchDropdown()
                        hideKeyboard()
                        if (p != null) {
                            controller.animateTo(p)
                            scheduleLookup()
                        }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
            )

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    if (skipNextLookupFromSearchPick) {
                        skipNextLookupFromSearchPick = false
                        return false
                    }
                    scheduleLookup()
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    if (skipNextLookupFromSearchPick) {
                        skipNextLookupFromSearchPick = false
                        return false
                    }
                    scheduleLookup()
                    return false
                }
            })
        }
    }

    private fun setupSearch() {
        searchAdapter = LocationAdapter(emptyList()) { chosen ->
            onSearchResultSelected(chosen)
        }
        binding.rvMapSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvMapSearchResults.adapter = searchAdapter

        binding.etMapSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                binding.btnClearMapSearch.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                handler.removeCallbacks(debouncedSearchRunnable)
                if (q.isBlank()) {
                    searchJob?.cancel()
                    hideSearchDropdown()
                } else {
                    showInstantOfflineMatches(q)
                    if (q.length >= 2) {
                        handler.postDelayed(debouncedSearchRunnable, 450L)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etMapSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerImmediateSearch()
                true
            } else {
                false
            }
        }

        binding.btnMapSearch.setOnClickListener { triggerImmediateSearch() }

        binding.btnClearMapSearch.setOnClickListener {
            binding.etMapSearch.setText("")
            hideSearchDropdown()
        }
    }

    private fun triggerImmediateSearch() {
        val q = binding.etMapSearch.text?.toString()?.trim().orEmpty()
        if (q.isBlank()) {
            Toast.makeText(this, "খোঁজার জন্য এলাকার নাম বা শহরের নাম লিখুন", Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        handler.removeCallbacks(debouncedSearchRunnable)
        performPlaceSearch(q)
    }

    private fun showInstantOfflineMatches(query: String) {
        val qLower = query.lowercase(Locale.ROOT)
        val quick = LocationData.allLocations.filter {
            it.nameBn.lowercase(Locale.ROOT).contains(qLower) ||
                it.nameEn.lowercase(Locale.ROOT).contains(qLower) ||
                it.parentBn.lowercase(Locale.ROOT).contains(qLower)
        }.take(8)
        if (quick.isNotEmpty()) {
            binding.cardMapSearchResults.visibility = View.VISIBLE
            binding.tvMapSearchEmpty.visibility = View.GONE
            searchAdapter.updateList(quick)
        }
    }

    private fun performPlaceSearch(query: String) {
        searchJob?.cancel()
        binding.cardMapSearchResults.visibility = View.VISIBLE
        binding.pbMapSearch.visibility = View.VISIBLE
        binding.tvMapSearchEmpty.visibility = View.GONE

        val center = binding.mapView.mapCenter
        searchJob = lifecycleScope.launch {
            val results = LocationHelper.searchPlacesUnlimited(
                context = this@MapPickerActivity,
                query = query,
                biasLat = center.latitude,
                biasLng = center.longitude
            )
            if (isFinishing || isDestroyed) return@launch
            binding.pbMapSearch.visibility = View.GONE
            searchAdapter.updateList(results)
            binding.tvMapSearchEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun onSearchResultSelected(chosen: District) {
        hideSearchDropdown()
        hideKeyboard()
        handler.removeCallbacks(lookupRunnable)
        skipNextLookupFromSearchPick = true

        val targetZoom = when (chosen.type) {
            LocationType.DIVISION -> 9.0
            LocationType.DISTRICT -> 12.0
            LocationType.THANA -> 14.0
            LocationType.GLOBAL_CITY -> 13.0
            LocationType.CUSTOM_GPS -> 16.5
        }

        binding.mapView.controller.setZoom(targetZoom)
        binding.mapView.controller.animateTo(GeoPoint(chosen.lat, chosen.lng))

        resolved = chosen
        binding.tvPlaceName.text = chosen.displayName.removePrefix("📍 ").trim()
        val coordsText = String.format(Locale.US, "%.5f, %.5f", chosen.lat, chosen.lng)
        binding.tvPlaceDetail.text = if (chosen.parentBn.isNotBlank()) {
            "${chosen.parentBn} • $coordsText"
        } else {
            coordsText
        }
        binding.pbPlace.visibility = View.INVISIBLE
        binding.btnConfirmPlace.isEnabled = true
    }

    private fun hideSearchDropdown() {
        binding.cardMapSearchResults.visibility = View.GONE
        binding.pbMapSearch.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etMapSearch.windowToken, 0)
    }

    private fun scheduleLookup() {
        handler.removeCallbacks(lookupRunnable)
        binding.btnConfirmPlace.isEnabled = false
        binding.tvPlaceName.text = "জায়গা খোঁজা হচ্ছে…"
        binding.pbPlace.visibility = View.VISIBLE
        handler.postDelayed(lookupRunnable, LOOKUP_DELAY_MS)
    }

    private fun resolveCenter() {
        val center = binding.mapView.mapCenter
        val lat = center.latitude
        val lng = center.longitude
        val id = ++requestId

        LocationHelper.resolveCoordinates(this, lat, lng) { district ->
            if (id != requestId || isFinishing || isDestroyed) return@resolveCoordinates
            resolved = district
            binding.tvPlaceName.text = district.displayName.removePrefix("📍 ").trim()
            val coordsText = String.format(Locale.US, "%.5f, %.5f", lat, lng)
            binding.tvPlaceDetail.text = if (district.parentBn.isNotBlank()) {
                "${district.parentBn} • $coordsText"
            } else {
                coordsText
            }
            binding.pbPlace.visibility = View.INVISIBLE
            binding.btnConfirmPlace.isEnabled = true
        }
    }

    private fun moveToMyLocation() {
        if (!LocationHelper.hasLocationPermission(this)) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        Toast.makeText(this, "📡 আপনার অবস্থান খোঁজা হচ্ছে...", Toast.LENGTH_SHORT).show()
        LocationHelper.fetchCurrentLocation(
            context = this,
            onSuccess = { district ->
                binding.mapView.controller.setZoom(17.0)
                binding.mapView.controller.animateTo(GeoPoint(district.lat, district.lng))
                scheduleLookup()
            },
            onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }

    private fun confirm() {
        val d = resolved ?: return
        val data = Intent().apply {
            putExtra(RESULT_NAME_BN, d.nameBn)
            putExtra(RESULT_NAME_EN, d.nameEn)
            putExtra(RESULT_PARENT_BN, d.parentBn)
            putExtra(RESULT_LAT, d.lat)
            putExtra(RESULT_LNG, d.lng)
            putExtra(RESULT_TZ, d.timeZone)
            putExtra(RESULT_METHOD, d.method.name)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        searchJob?.cancel()
        binding.mapView.onDetach()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LAT = "map_start_lat"
        const val EXTRA_LNG = "map_start_lng"
        const val RESULT_NAME_BN = "map_result_name_bn"
        const val RESULT_NAME_EN = "map_result_name_en"
        const val RESULT_PARENT_BN = "map_result_parent_bn"
        const val RESULT_LAT = "map_result_lat"
        const val RESULT_LNG = "map_result_lng"
        const val RESULT_TZ = "map_result_tz"
        const val RESULT_METHOD = "map_result_method"
        private const val LOOKUP_DELAY_MS = 1000L
    }
}
