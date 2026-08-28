package com.hafij.islamicalarm.prayertimes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Gets the current GPS location with high reliability:
     * 1. FusedLocationProviderClient lastLocation
     * 2. Single high-accuracy getCurrentLocation request
     * 3. Fallback to LocationManager GPS / Network providers
     */
    fun fetchCurrentLocation(
        context: Context,
        onSuccess: (District) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onError("লোকেশন পারমিশন দেওয়া হয়নি। অনুগ্রহ করে পারমিশন দিন।")
            return
        }

        if (!isLocationEnabled(context)) {
            onError("লোকেশন সার্ভিস (GPS) বন্ধ আছে। অনুগ্রহ করে ফোনের সেটিংস থেকে লোকেশন চালু করুন।")
            return
        }

        val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        try {
            fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                if (lastLoc != null && isRecent(lastLoc)) {
                    resolveLocationToDistrict(context, lastLoc, onSuccess)
                } else {
                    requestFreshLocation(context, fusedClient, onSuccess, onError)
                }
            }.addOnFailureListener {
                requestFreshLocation(context, fusedClient, onSuccess, onError)
            }
        } catch (e: SecurityException) {
            onError("পারমিশন নিরাপত্তা ত্রুটি: ${e.localizedMessage}")
        } catch (e: Exception) {
            fallbackToLocationManager(context, onSuccess, onError)
        }
    }

    private fun isRecent(location: Location): Boolean {
        // within 2 hours
        return (System.currentTimeMillis() - location.time) < 2 * 60 * 60 * 1000
    }

    private fun requestFreshLocation(
        context: Context,
        fusedClient: FusedLocationProviderClient,
        onSuccess: (District) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMaxUpdates(1)
                .setWaitForAccurateLocation(false)
                .build()

            var alreadyHandled = false
            val timeoutHandler = android.os.Handler(Looper.getMainLooper())

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (alreadyHandled) return
                    alreadyHandled = true
                    timeoutHandler.removeCallbacksAndMessages(null)
                    fusedClient.removeLocationUpdates(this)
                    val loc = result.lastLocation
                    if (loc != null) {
                        resolveLocationToDistrict(context, loc, onSuccess)
                    } else {
                        fallbackToLocationManager(context, onSuccess, onError)
                    }
                }
            }

            // Safety timeout: some devices/providers never call back (GPS weak signal,
            // Play Services issue, etc). Without this the app would hang indefinitely.
            timeoutHandler.postDelayed({
                if (!alreadyHandled) {
                    alreadyHandled = true
                    try {
                        fusedClient.removeLocationUpdates(callback)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    fallbackToLocationManager(context, onSuccess, onError)
                }
            }, 10000L)

            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    if (!alreadyHandled) {
                        alreadyHandled = true
                        timeoutHandler.removeCallbacksAndMessages(null)
                        fallbackToLocationManager(context, onSuccess, onError)
                    }
                }
        } catch (e: SecurityException) {
            onError("লোকেশন পারমিশন প্রয়োজন")
        } catch (e: Exception) {
            fallbackToLocationManager(context, onSuccess, onError)
        }
    }

    private fun fallbackToLocationManager(
        context: Context,
        onSuccess: (District) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                onError("লোকেশন সেবা পাওয়া যায়নি")
                return
            }

            var bestLocation: Location? = null

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }

            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (bestLocation != null) {
                resolveLocationToDistrict(context, bestLocation, onSuccess)
            } else {
                onError("জিপিএস লোকেশন শনাক্ত করা যায়নি। অনুগ্রহ করে জিপিএস চালু করুন।")
            }
        } catch (e: SecurityException) {
            onError("লোকেশন পারমিশন নিরাপত্তা ত্রুটি")
        } catch (e: Exception) {
            onError("লোকেশন নির্ধারণে সমস্যা হয়েছে")
        }
    }

    private fun resolveLocationToDistrict(
        context: Context,
        location: Location,
        onSuccess: (District) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val lat = location.latitude
            val lng = location.longitude

            var placeNameBn = ""
            var placeNameEn = ""
            var parentAreaBn = ""
            var countryName = ""

            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale("bn", "BD"))
                    val addresses = geocoder.getFromLocation(lat, lng, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val address: Address = addresses[0]
                        val subLocality = address.subLocality // e.g. Thana or neighborhood
                        val locality = address.locality // e.g. City
                        val subAdmin = address.subAdminArea // e.g. District
                        val admin = address.adminArea // e.g. Division / State
                        countryName = address.countryName ?: ""

                        placeNameBn = subLocality ?: locality ?: subAdmin ?: admin ?: ""
                        parentAreaBn = subAdmin ?: admin ?: countryName
                        placeNameEn = address.featureName ?: ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Detect timezone offset
            val tz = TimeZone.getDefault()
            val timeZoneOffsetHours = tz.getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)

            // Select calculation method
            val isBangladesh = (lat in 20.5..26.8 && lng in 88.0..92.8) || countryName.contains("Bangladesh", ignoreCase = true) || countryName.contains("বাংলাদেশ", ignoreCase = true)
            val method = if (isBangladesh) {
                CalculationMethod.KARACHI_BD
            } else if (countryName.contains("Saudi", ignoreCase = true) || countryName.contains("সৌদি", ignoreCase = true)) {
                CalculationMethod.UMM_AL_QURA
            } else if (countryName.contains("Egypt", ignoreCase = true) || countryName.contains("মিশর", ignoreCase = true)) {
                CalculationMethod.EGYPTIAN
            } else if (countryName.contains("United States", ignoreCase = true) || countryName.contains("Canada", ignoreCase = true)) {
                CalculationMethod.ISNA
            } else {
                CalculationMethod.MUSLIM_WORLD_LEAGUE
            }

            // Find closest preset or construct exact location
            val closest = LocationData.findClosestLocation(lat, lng)
            val finalNameBn = if (placeNameBn.isNotBlank()) placeNameBn else closest.nameBn
            val finalParentBn = if (parentAreaBn.isNotBlank()) parentAreaBn else closest.parentBn

            val resultDistrict = District(
                nameEn = if (placeNameEn.isNotBlank()) placeNameEn else closest.nameEn,
                nameBn = finalNameBn,
                divisionBn = closest.divisionBn,
                lat = lat,
                lng = lng,
                offsetMinutes = closest.offsetMinutes,
                timeZone = if (isBangladesh) 6.0 else timeZoneOffsetHours,
                type = LocationType.CUSTOM_GPS,
                parentBn = finalParentBn,
                method = method
            )

            withContext(Dispatchers.Main) {
                onSuccess(resultDistrict)
            }
        }
    }

    /**
     * Search any location globally using Android Geocoder API online
     */
    suspend fun searchOnline(context: Context, query: String): List<District> = withContext(Dispatchers.IO) {
        val results = mutableListOf<District>()
        if (query.isBlank()) return@withContext results

        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(query, 5)

                if (!addresses.isNullOrEmpty()) {
                    for (addr in addresses) {
                        val name = addr.locality ?: addr.subLocality ?: addr.subAdminArea ?: addr.featureName ?: query
                        val parent = addr.adminArea ?: addr.countryName ?: ""
                        val country = addr.countryName ?: ""
                        val lat = addr.latitude
                        val lng = addr.longitude

                        val isBd = (lat in 20.5..26.8 && lng in 88.0..92.8) || country.contains("Bangladesh", ignoreCase = true)
                        val tz = if (isBd) 6.0 else {
                            val detectedTz = TimeZone.getDefault()
                            detectedTz.getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)
                        }

                        val method = if (isBd) {
                            CalculationMethod.KARACHI_BD
                        } else if (country.contains("Saudi", ignoreCase = true)) {
                            CalculationMethod.UMM_AL_QURA
                        } else if (country.contains("Egypt", ignoreCase = true)) {
                            CalculationMethod.EGYPTIAN
                        } else if (country.contains("United States", ignoreCase = true) || country.contains("Canada", ignoreCase = true)) {
                            CalculationMethod.ISNA
                        } else {
                            CalculationMethod.MUSLIM_WORLD_LEAGUE
                        }

                        results.add(
                            District(
                                nameEn = name,
                                nameBn = name,
                                divisionBn = parent,
                                lat = lat,
                                lng = lng,
                                offsetMinutes = 0,
                                timeZone = tz,
                                type = LocationType.GLOBAL_CITY,
                                parentBn = parent,
                                method = method
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext results
    }
}
