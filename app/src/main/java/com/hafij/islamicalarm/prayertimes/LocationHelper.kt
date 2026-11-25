package com.hafij.islamicalarm.prayertimes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone

object LocationHelper {

    internal data class ResolvedSpot(
        val spotBn: String,
        val parentBn: String,
        val spotEn: String = "",
        val countryName: String = ""
    )

    private val PLUS_CODE_REGEX = Regex("^[A-Z0-9]{4,8}\\+[A-Z0-9]{2,4}.*$")
    private val GENERIC_PLACE_NAMES = setOf(
        "unnamed road", "unnamed", "bangladesh", "বাংলাদেশ", "road", "street"
    )

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
     * Checks if GPS is enabled on the device. If not, requests Google Play Services
     * to prompt the user to enable GPS with a single click in a system dialog.
     */
    fun checkAndPromptEnableGps(
        activity: android.app.Activity,
        onGpsAlreadyEnabled: () -> Unit,
        onResolutionRequired: (com.google.android.gms.common.api.ResolvableApiException) -> Unit,
        onGpsUnavailable: () -> Unit
    ) {
        if (isLocationEnabled(activity)) {
            onGpsAlreadyEnabled()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .build()
        val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            onGpsAlreadyEnabled()
        }

        task.addOnFailureListener { exception ->
            if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                onResolutionRequired(exception)
            } else {
                onGpsUnavailable()
            }
        }
    }

    /**
     * Gets the user's exact current GPS position:
     * 1. High-accuracy fresh current fix via FusedLocationProviderClient.getCurrentLocation
     * 2. Only reuses cached lastLocation if newer than 2 minutes AND accuracy <= 65 meters
     * 3. Active high-accuracy LocationRequest / LocationManager single update fallback
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
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { currentLoc: Location? ->
                    if (currentLoc != null && (!currentLoc.hasAccuracy() || currentLoc.accuracy <= 150f)) {
                        resolveLocationToDistrict(context, currentLoc, onSuccess)
                    } else {
                        tryCachedOrActiveUpdate(context, fusedClient, onSuccess, onError)
                    }
                }
                .addOnFailureListener {
                    tryCachedOrActiveUpdate(context, fusedClient, onSuccess, onError)
                }
        } catch (e: SecurityException) {
            onError("পারমিশন নিরাপত্তা ত্রুটি: ${e.localizedMessage}")
        } catch (_: Exception) {
            tryCachedOrActiveUpdate(context, fusedClient, onSuccess, onError)
        }
    }

    private fun tryCachedOrActiveUpdate(
        context: Context,
        fusedClient: FusedLocationProviderClient,
        onSuccess: (District) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                if (lastLoc != null && isRecentAndPrecise(lastLoc)) {
                    resolveLocationToDistrict(context, lastLoc, onSuccess)
                } else {
                    requestFreshLocation(context, fusedClient, onSuccess, onError)
                }
            }.addOnFailureListener {
                requestFreshLocation(context, fusedClient, onSuccess, onError)
            }
        } catch (_: Exception) {
            fallbackToLocationManager(context, onSuccess, onError)
        }
    }

    internal fun isRecentAndPrecise(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        val isFresh = ageMs in 0L..(2 * 60 * 1000L)
        val isAccurate = location.hasAccuracy() && location.accuracy <= 65f
        return isFresh && isAccurate
    }

    private fun requestFreshLocation(
        context: Context,
        fusedClient: FusedLocationProviderClient,
        onSuccess: (District) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdates(2)
                .setWaitForAccurateLocation(true)
                .build()

            var alreadyHandled = false
            val timeoutHandler = Handler(Looper.getMainLooper())
            var bestSeen: Location? = null

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    if (bestSeen == null || (loc.hasAccuracy() && (!bestSeen!!.hasAccuracy() || loc.accuracy < bestSeen!!.accuracy))) {
                        bestSeen = loc
                    }
                    // Wait for reasonably tight accuracy if first update was coarse; otherwise finish immediately
                    if (!alreadyHandled && (!loc.hasAccuracy() || loc.accuracy <= 80f)) {
                        alreadyHandled = true
                        timeoutHandler.removeCallbacksAndMessages(null)
                        fusedClient.removeLocationUpdates(this)
                        resolveLocationToDistrict(context, loc, onSuccess)
                    }
                }
            }

            timeoutHandler.postDelayed({
                if (!alreadyHandled) {
                    alreadyHandled = true
                    try {
                        fusedClient.removeLocationUpdates(callback)
                    } catch (_: Exception) {
                    }
                    val seen = bestSeen
                    if (seen != null) {
                        resolveLocationToDistrict(context, seen, onSuccess)
                    } else {
                        fallbackToLocationManager(context, onSuccess, onError)
                    }
                }
            }, 9000L)

            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    if (!alreadyHandled) {
                        alreadyHandled = true
                        timeoutHandler.removeCallbacksAndMessages(null)
                        fallbackToLocationManager(context, onSuccess, onError)
                    }
                }
        } catch (_: SecurityException) {
            onError("লোকেশন পারমিশন প্রয়োজন")
        } catch (_: Exception) {
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

            val candidates = mutableListOf<Location>()
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { candidates.add(it) }
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { candidates.add(it) }
            }

            val recentBest = candidates
                .filter { isRecentAndPrecise(it) }
                .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }

            if (recentBest != null) {
                resolveLocationToDistrict(context, recentBest, onSuccess)
                return
            }

            // Try an active single LocationManager update when cached fix is stale/missing
            val activeProvider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (activeProvider != null) {
                var handled = false
                val handler = Handler(Looper.getMainLooper())
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        if (handled) return
                        handled = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {
                        }
                        resolveLocationToDistrict(context, loc, onSuccess)
                    }

                    @Deprecated("Deprecated in Android API")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }

                handler.postDelayed({
                    if (!handled) {
                        handled = true
                        try {
                            locationManager.removeUpdates(listener)
                        } catch (_: Exception) {
                        }
                        val fallbackCached = candidates.maxByOrNull { it.time }
                        if (fallbackCached != null) {
                            resolveLocationToDistrict(context, fallbackCached, onSuccess)
                        } else {
                            onError("জিপিএস লোকেশন শনাক্ত করা যায়নি। খোলা আকাশের নিচে চেষ্টা করুন।")
                        }
                    }
                }, 6000L)

                locationManager.requestLocationUpdates(activeProvider, 0L, 0f, listener, Looper.getMainLooper())
            } else {
                val fallbackCached = candidates.maxByOrNull { it.time }
                if (fallbackCached != null) {
                    resolveLocationToDistrict(context, fallbackCached, onSuccess)
                } else {
                    onError("জিপিএস লোকেশন শনাক্ত করা যায়নি। অনুগ্রহ করে জিপিএস চালু করুন।")
                }
            }
        } catch (_: SecurityException) {
            onError("লোকেশন পারমিশন নিরাপত্তা ত্রুটি")
        } catch (_: Exception) {
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

            // 1. Primary: BigDataCloud's boundary-based reverse geocoder. Unlike Nominatim
            // (whose usage policy explicitly forbids bulk calls from a mobile app installed
            // on many devices, so it silently rate-limits/fails for real users) or Android's
            // built-in Geocoder (backed by Google's database, which often has no street/POI
            // data for rural upazila/union areas and falls back to thana/district), this
            // endpoint is designed specifically for client apps using the device's live GPS,
            // resolves to administrative/village-level boundaries, and works reliably even
            // in less-populated areas — this is what other apps (e.g. Muslim Day) rely on
            // to show the exact spot instead of just the thana/district.
            var resolved = fetchBigDataCloudSpot(lat, lng)

            // 2. Supplement any still-blank fields with high-precision Nominatim reverse
            // geocode (zoom=18, street/neighbourhood level).
            val nominatimSpot = fetchNominatimSpot(lat, lng)
            resolved = mergeResolvedSpots(primary = resolved, secondary = nominatimSpot)

            // 3. Final fallback with Android Geocoder (bn_BD, then device default)
            val geocoderSpot = fetchAndroidGeocoderSpot(context, lat, lng)
            resolved = mergeResolvedSpots(primary = resolved, secondary = geocoderSpot)

            val tz = TimeZone.getDefault()
            val timeZoneOffsetHours = tz.getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)

            val countryName = resolved?.countryName.orEmpty()
            val isBangladesh = (lat in 20.5..26.8 && lng in 88.0..92.8) ||
                countryName.contains("Bangladesh", ignoreCase = true) ||
                countryName.contains("বাংলাদেশ", ignoreCase = true)

            val method = when {
                isBangladesh -> CalculationMethod.KARACHI_BD
                countryName.contains("Saudi", ignoreCase = true) || countryName.contains("সৌদি", ignoreCase = true) ->
                    CalculationMethod.UMM_AL_QURA
                countryName.contains("Egypt", ignoreCase = true) || countryName.contains("মিশর", ignoreCase = true) ->
                    CalculationMethod.EGYPTIAN
                countryName.contains("United States", ignoreCase = true) || countryName.contains("Canada", ignoreCase = true) ->
                    CalculationMethod.ISNA
                else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            }

            // Use closest local entry ONLY for division/timezone fallback metadata, NEVER to override spot name or exact coords
            val closest = LocationData.findClosestLocation(lat, lng)
            val finalSpotBn = resolved?.spotBn?.takeIf { it.isNotBlank() } ?: closest.nameBn
            val finalParentBn = resolved?.parentBn?.takeIf { it.isNotBlank() } ?: closest.parentBn
            val formattedExactName = if (
                finalParentBn.isNotBlank() &&
                finalParentBn != "বাংলাদেশ" &&
                !finalSpotBn.contains(finalParentBn, ignoreCase = true)
            ) {
                "$finalSpotBn, $finalParentBn"
            } else {
                finalSpotBn
            }

            val resultDistrict = District(
                nameEn = resolved?.spotEn?.takeIf { it.isNotBlank() } ?: formattedExactName,
                nameBn = formattedExactName,
                divisionBn = closest.divisionBn,
                lat = lat,
                lng = lng,
                offsetMinutes = 0,
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
     * https://api.bigdatacloud.net/data/reverse-geocode-client — free, no API key, and its
     * own Fair Use Policy explicitly allows exactly this: a mobile app calling it directly
     * from the client using the device's current GPS fix. It resolves to administrative/
     * village boundaries rather than street addresses, so it stays accurate in rural areas
     * where other providers only have thana/district-level data.
     */
    private fun fetchBigDataCloudSpot(lat: Double, lng: Double): ResolvedSpot? {
        var conn: HttpURLConnection? = null
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.6f&longitude=%.6f&localityLanguage=bn",
                lat,
                lng
            )
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                requestMethod = "GET"
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            extractExactPlaceFromBigDataCloudJson(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun extractExactPlaceFromBigDataCloudJson(jsonStr: String): ResolvedSpot? {
        if (jsonStr.isBlank()) return null
        return try {
            val root = JSONObject(jsonStr)
            val country = cleanToken(root.optString("countryName"))
            val locality = cleanToken(root.optString("locality"))
            val city = cleanToken(root.optString("city"))
            val principalSubdivision = cleanToken(root.optString("principalSubdivision"))

            // "administrative" is ordered most-significant-first (country -> division ->
            // district -> upazila -> union/ward), so the last usable entry is the finest
            // named boundary and the one before it is the next broader level.
            val adminNames = mutableListOf<String>()
            val adminArr = root.optJSONObject("localityInfo")?.optJSONArray("administrative")
            if (adminArr != null) {
                for (i in 0 until adminArr.length()) {
                    val name = cleanToken(adminArr.optJSONObject(i)?.optString("name"))
                    if (name != null && isUsablePlaceToken(name)) adminNames.add(name)
                }
            }
            val adminMostGranular = adminNames.lastOrNull()
            val adminBroader = if (adminNames.size >= 2) adminNames[adminNames.size - 2] else null

            val spot = listOfNotNull(locality, city, adminMostGranular)
                .firstOrNull { isUsablePlaceToken(it) && !it.equals(country, ignoreCase = true) }
                ?: return null

            val parent = listOfNotNull(
                principalSubdivision,
                adminBroader,
                city?.takeIf { !it.equals(spot, ignoreCase = true) },
                country
            ).firstOrNull { isUsablePlaceToken(it) && !it.equals(spot, ignoreCase = true) } ?: country.orEmpty()

            ResolvedSpot(
                spotBn = spot,
                parentBn = parent,
                spotEn = spot,
                countryName = country.orEmpty()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchNominatimSpot(lat: Double, lng: Double): ResolvedSpot? {
        var conn: HttpURLConnection? = null
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1&accept-language=bn,en",
                lat,
                lng
            )
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "IslamicAlarmAndroid/1.0")
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            extractExactPlaceFromNominatimJson(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchAndroidGeocoderSpot(context: Context, lat: Double, lng: Double): ResolvedSpot? {
        if (!Geocoder.isPresent()) return null
        return try {
            val bnGeocoder = Geocoder(context, Locale("bn", "BD"))
            @Suppress("DEPRECATION")
            val bnAddresses = bnGeocoder.getFromLocation(lat, lng, 3).orEmpty()
            extractExactPlaceFromAddresses(bnAddresses)
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractExactPlaceFromNominatimJson(jsonStr: String): ResolvedSpot? {
        if (jsonStr.isBlank()) return null
        return try {
            val root = JSONObject(jsonStr)
            val addr = root.optJSONObject("address") ?: return null

            val country = cleanToken(addr.optString("country"))
            val microCandidates = listOf(
                addr.optString("neighbourhood"),
                addr.optString("quarter"),
                addr.optString("residential"),
                addr.optString("village"),
                addr.optString("hamlet"),
                addr.optString("suburb"),
                addr.optString("road"),
                root.optString("name"),
                addr.optString("city_district"),
                addr.optString("town"),
                addr.optString("city")
            ).mapNotNull { cleanToken(it) }.filter { isUsablePlaceToken(it) }

            val parentCandidates = listOf(
                addr.optString("city"),
                addr.optString("town"),
                addr.optString("county"),
                addr.optString("state_district"),
                addr.optString("state"),
                country
            ).mapNotNull { cleanToken(it) }.filter { isUsablePlaceToken(it) }

            val spot = microCandidates.firstOrNull() ?: return null
            val parent = parentCandidates.firstOrNull { !it.equals(spot, ignoreCase = true) }
                ?: parentCandidates.firstOrNull().orEmpty()

            ResolvedSpot(
                spotBn = spot,
                parentBn = parent,
                spotEn = spot,
                countryName = country.orEmpty()
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractExactPlaceFromAddresses(addresses: List<Address>): ResolvedSpot? {
        if (addresses.isEmpty()) return null

        val specificTokens = mutableListOf<String>()
        val parentTokens = mutableListOf<String>()
        var country = ""

        for (addr in addresses) {
            if (country.isBlank()) country = cleanToken(addr.countryName).orEmpty()

            // Micro-level candidates first: road/para/village/neighborhood/landmark BEFORE broad subLocality/District
            val candidates = listOf(
                addr.subLocality,
                addr.thoroughfare,
                addr.featureName,
                addr.locality,
                addr.subAdminArea,
                addr.adminArea
            )
            // Prioritize subLocality or thoroughfare when distinct from locality/subAdminArea (district)
            val districtOrCity = setOfNotNull(
                cleanToken(addr.subAdminArea)?.lowercase(Locale.ROOT),
                cleanToken(addr.adminArea)?.lowercase(Locale.ROOT)
            )
            val orderedSpecific = listOf(
                addr.thoroughfare,
                addr.subLocality,
                addr.featureName,
                addr.locality,
                addr.subAdminArea,
                addr.adminArea
            ).mapNotNull { cleanToken(it) }
                .filter { isUsablePlaceToken(it) && !isPureNumber(it) }

            // Put tokens that are NOT identical to the broad district/division at the front
            val strictlyLocal = orderedSpecific.filter { it.lowercase(Locale.ROOT) !in districtOrCity }
            for (tok in strictlyLocal) {
                if (tok !in specificTokens) specificTokens.add(tok)
            }
            for (tok in orderedSpecific) {
                if (tok !in specificTokens) specificTokens.add(tok)
            }

            listOf(addr.locality, addr.subAdminArea, addr.adminArea, addr.countryName)
                .mapNotNull { cleanToken(it) }
                .filter { isUsablePlaceToken(it) && !isPureNumber(it) }
                .forEach { tok -> if (tok !in parentTokens) parentTokens.add(tok) }

            // Also parse address lines if thoroughfare/subLocality were blank (common in Bangladesh Google Geocoder)
            val line0 = cleanToken(addr.getAddressLine(0))
            if (!line0.isNullOrBlank()) {
                line0.split(",")
                    .mapNotNull { cleanToken(it) }
                    .filter { isUsablePlaceToken(it) && !isPureNumber(it) }
                    .firstOrNull { it.lowercase(Locale.ROOT) !in districtOrCity }
                    ?.let { tok ->
                        if (tok !in specificTokens) specificTokens.add(0, tok)
                    }
            }
        }

        val spot = specificTokens.firstOrNull() ?: return null
        val parent = parentTokens.firstOrNull { !it.equals(spot, ignoreCase = true) }
            ?: parentTokens.firstOrNull().orEmpty()

        return ResolvedSpot(
            spotBn = spot,
            parentBn = parent,
            spotEn = spot,
            countryName = country
        )
    }

    internal fun mergeResolvedSpots(primary: ResolvedSpot?, secondary: ResolvedSpot?): ResolvedSpot? {
        if (primary == null) return secondary
        if (secondary == null) return primary
        return ResolvedSpot(
            spotBn = primary.spotBn.ifBlank { secondary.spotBn },
            parentBn = primary.parentBn.ifBlank { secondary.parentBn },
            spotEn = primary.spotEn.ifBlank { secondary.spotEn },
            countryName = primary.countryName.ifBlank { secondary.countryName }
        )
    }

    private fun cleanToken(raw: String?): String? {
        val t = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        return t.takeIf { it.isNotBlank() }
    }

    private fun isPureNumber(s: String): Boolean {
        val cleaned = s.replace("-", "").replace(" ", "")
        return cleaned.isNotEmpty() && cleaned.all { it.isDigit() || it in '০'..'৯' }
    }

    private fun isUsablePlaceToken(raw: String): Boolean {
        val s = raw.trim()
        if (s.length < 2) return false
        if (PLUS_CODE_REGEX.matches(s)) return false
        if (s.lowercase(Locale.ROOT) in GENERIC_PLACE_NAMES) return false
        return true
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
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 5)

                if (!addresses.isNullOrEmpty()) {
                    for (addr in addresses) {
                        val name = addr.subLocality ?: addr.thoroughfare ?: addr.locality ?: addr.subAdminArea ?: addr.featureName ?: query
                        val parent = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName ?: ""
                        val country = addr.countryName ?: ""
                        val lat = addr.latitude
                        val lng = addr.longitude

                        val isBd = (lat in 20.5..26.8 && lng in 88.0..92.8) || country.contains("Bangladesh", ignoreCase = true)
                        val tz = if (isBd) 6.0 else {
                            val detectedTz = TimeZone.getDefault()
                            detectedTz.getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)
                        }

                        val method = when {
                            isBd -> CalculationMethod.KARACHI_BD
                            country.contains("Saudi", ignoreCase = true) -> CalculationMethod.UMM_AL_QURA
                            country.contains("Egypt", ignoreCase = true) -> CalculationMethod.EGYPTIAN
                            country.contains("United States", ignoreCase = true) || country.contains("Canada", ignoreCase = true) -> CalculationMethod.ISNA
                            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
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
