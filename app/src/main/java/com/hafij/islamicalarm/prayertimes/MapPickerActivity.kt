package com.hafij.islamicalarm.prayertimes

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hafij.islamicalarm.databinding.ActivityMapPickerBinding
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import java.io.File
import java.util.Locale

/**
 * Full-screen OpenStreetMap picker. The user moves the map under a fixed pin; the place
 * name under the pin is looked up (village / para first) and shown. "Confirm" returns the
 * chosen point to the caller, which then calculates prayer times for exactly that point.
 */
class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var requestId = 0
    private var resolved: District? = null

    private val lookupRunnable = Runnable { resolveCenter() }

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

        // Keep the map cache inside the app (no storage permission needed) and identify the app
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hasStart = intent.hasExtra(EXTRA_LAT) && intent.hasExtra(EXTRA_LNG)
        val startLat = intent.getDoubleExtra(EXTRA_LAT, 23.8103)
        val startLng = intent.getDoubleExtra(EXTRA_LNG, 90.4125)

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(if (hasStart) 17.0 else 7.0)
            controller.setCenter(GeoPoint(startLat, startLng))
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    scheduleLookup()
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    scheduleLookup()
                    return false
                }
            })
        }

        binding.btnMapBack.setOnClickListener { finish() }
        binding.btnMyLocation.setOnClickListener { moveToMyLocation() }
        binding.btnConfirmPlace.setOnClickListener { confirm() }

        scheduleLookup()
    }

    private fun scheduleLookup() {
        // Wait until the finger stops, and respect Nominatim's one-request-per-second rule
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
            binding.tvPlaceDetail.text = String.format(Locale.US, "%.5f, %.5f", lat, lng)
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
        private const val LOOKUP_DELAY_MS = 1300L
    }
}
