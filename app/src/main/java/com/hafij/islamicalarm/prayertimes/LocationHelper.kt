package com.hafij.islamicalarm.prayertimes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object LocationHelper {

    internal const val GRANULARITY_MICRO = 1
    internal const val GRANULARITY_LOCALITY = 2
    internal const val GRANULARITY_BROAD = 3

    private const val APP_USER_AGENT = "IslamicAlarm/1.0 (com.hafij.islamicalarm; android-app)"

    internal data class ResolvedSpot(
        val spotBn: String,
        val parentBn: String,
        val spotEn: String = "",
        val countryName: String = "",
        val granularityTier: Int = GRANULARITY_BROAD
    )

    private val PLUS_CODE_REGEX = Regex("^[A-Z0-9]{4,8}\\+[A-Z0-9]{2,4}.*$")
    private val GENERIC_PLACE_NAMES = setOf(
        "unnamed road", "unnamed", "bangladesh", "বাংলাদেশ", "road", "street"
    )
    private val BROAD_ADMIN_SUFFIXES = listOf(
        "থানা", "উপজেলা", "জেলা", "বিভাগ", "মহানগর",
        "thana", "upazila", "zila", "zilla", "district", "division", "city corporation", "sadar", "সদর"
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
                    if (currentLoc != null && (!currentLoc.hasAccuracy() || currentLoc.accuracy <= 120f)) {
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

    /**
     * Resolves a chosen map point (lat/lng) to a place name and a District, using the same
     * micro-spot-first lookup as the GPS button. [onSuccess] is called on the main thread.
     */
    fun resolveCoordinates(
        context: Context,
        lat: Double,
        lng: Double,
        onSuccess: (District) -> Unit
    ) {
        val point = Location("map_picker").apply {
            latitude = lat
            longitude = lng
        }
        resolveLocationToDistrict(context, point, onSuccess)
    }

    private fun resolveLocationToDistrict(
        context: Context,
        location: Location,
        onSuccess: (District) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val lat = location.latitude
            val lng = location.longitude

            // Query all independent reverse-geocoding sources in parallel so micro-level
            // village/para/road names from Android Geocoder or Photon ALWAYS beat broad
            // town/thana/district labels from Nominatim/BigDataCloud.
            val resolved = coroutineScope {
                val geoDeferred = async { fetchAndroidGeocoderSpot(context, lat, lng) }
                val nomDeferred = async { fetchNominatimSpot(lat, lng) }
                val photonDeferred = async { fetchPhotonReverseSpot(lat, lng) }
                val bdcDeferred = async { fetchBigDataCloudSpot(lat, lng) }

                val candidates = listOfNotNull(
                    geoDeferred.await(),
                    nomDeferred.await(),
                    photonDeferred.await(),
                    bdcDeferred.await()
                )

                var best: ResolvedSpot? = null
                for (candidate in candidates) {
                    best = mergeResolvedSpots(best, candidate)
                }
                best
            }

            val tz = TimeZone.getDefault()
            val timeZoneOffsetHours = tz.getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)

            val countryName = resolved?.countryName.orEmpty()
            val isBangladesh = (lat in 20.5..26.8 && lng in 88.0..92.8) ||
                countryName.contains("Bangladesh", ignoreCase = true) ||
                countryName.contains("বাংলাদেশ", ignoreCase = true)

            val method = inferCalculationMethod(lat, lng, countryName)

            val closest = LocationData.findClosestLocation(lat, lng)
            val finalSpotBn = resolved?.spotBn?.takeIf { it.isNotBlank() } ?: closest.nameBn
            val finalParentBn = resolved?.parentBn?.takeIf { it.isNotBlank() } ?: closest.parentBn

            val resultDistrict = District(
                nameEn = resolved?.spotEn?.takeIf { it.isNotBlank() } ?: finalSpotBn,
                nameBn = finalSpotBn,
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

    private fun inferCalculationMethod(lat: Double, lng: Double, countryName: String): CalculationMethod {
        val isBd = (lat in 20.5..26.8 && lng in 88.0..92.8) ||
            countryName.contains("Bangladesh", ignoreCase = true) ||
            countryName.contains("বাংলাদেশ", ignoreCase = true)
        return when {
            isBd -> CalculationMethod.KARACHI_BD
            countryName.contains("Saudi", ignoreCase = true) || countryName.contains("সৌদি", ignoreCase = true) ->
                CalculationMethod.UMM_AL_QURA
            countryName.contains("Egypt", ignoreCase = true) || countryName.contains("মিশর", ignoreCase = true) ->
                CalculationMethod.EGYPTIAN
            countryName.contains("United States", ignoreCase = true) || countryName.contains("Canada", ignoreCase = true) ->
                CalculationMethod.ISNA
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
    }

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
                setRequestProperty("User-Agent", APP_USER_AGENT)
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

            val tier = if (locality != null && spot == locality && !isBroadAdminName(spot)) {
                GRANULARITY_LOCALITY
            } else {
                GRANULARITY_BROAD
            }

            ResolvedSpot(
                spotBn = spot,
                parentBn = parent,
                spotEn = spot,
                countryName = country.orEmpty(),
                granularityTier = tier
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
                setRequestProperty("User-Agent", APP_USER_AGENT)
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

    private fun fetchPhotonReverseSpot(lat: Double, lng: Double): ResolvedSpot? {
        var conn: HttpURLConnection? = null
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f",
                lat,
                lng
            )
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                requestMethod = "GET"
                setRequestProperty("User-Agent", APP_USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            extractExactPlaceFromPhotonJson(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun extractExactPlaceFromPhotonJson(jsonStr: String): ResolvedSpot? {
        if (jsonStr.isBlank()) return null
        return try {
            val root = JSONObject(jsonStr)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null
            val props = features.optJSONObject(0)?.optJSONObject("properties") ?: return null

            val country = cleanToken(props.optString("country"))
            val name = cleanToken(props.optString("name"))
            val street = cleanToken(props.optString("street"))
            val locality = cleanToken(props.optString("locality"))
            val district = cleanToken(props.optString("district"))
            val city = cleanToken(props.optString("city"))
            val county = cleanToken(props.optString("county"))
            val state = cleanToken(props.optString("state"))

            val microCandidate = listOfNotNull(name, street)
                .firstOrNull { isUsablePlaceToken(it) && !isBroadAdminName(it) }
            val localityCandidate = listOfNotNull(locality, district)
                .firstOrNull { isUsablePlaceToken(it) && !isBroadAdminName(it) }
            val broadCandidate = listOfNotNull(locality, district, city, county, state)
                .firstOrNull { isUsablePlaceToken(it) }

            val spot = microCandidate ?: localityCandidate ?: broadCandidate ?: return null
            val tier = when {
                microCandidate != null -> GRANULARITY_MICRO
                localityCandidate != null -> GRANULARITY_LOCALITY
                else -> GRANULARITY_BROAD
            }
            val parent = listOfNotNull(district, city, county, state, country)
                .firstOrNull { isUsablePlaceToken(it) && !it.equals(spot, ignoreCase = true) }
                ?: country.orEmpty()

            ResolvedSpot(
                spotBn = spot,
                parentBn = parent,
                spotEn = spot,
                countryName = country.orEmpty(),
                granularityTier = tier
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchAndroidGeocoderSpot(context: Context, lat: Double, lng: Double): ResolvedSpot? {
        if (!Geocoder.isPresent()) return null
        var best: ResolvedSpot? = null
        val locales = listOf(Locale("bn", "BD"), Locale.ENGLISH)
        for (loc in locales) {
            try {
                val geocoder = Geocoder(context, loc)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 5).orEmpty()
                val candidate = extractExactPlaceFromAddresses(addresses)
                best = mergeResolvedSpots(best, candidate)
                if (best != null && best.granularityTier == GRANULARITY_MICRO && loc.language == "bn") {
                    break
                }
            } catch (_: Exception) {
                // continue to next locale
            }
        }
        return best
    }

    internal fun extractExactPlaceFromNominatimJson(jsonStr: String): ResolvedSpot? {
        if (jsonStr.isBlank()) return null
        return try {
            val root = JSONObject(jsonStr)
            val addr = root.optJSONObject("address") ?: return null

            val country = cleanToken(addr.optString("country"))

            val microFields = listOf(
                addr.optString("village"),
                addr.optString("hamlet"),
                addr.optString("neighbourhood"),
                addr.optString("quarter"),
                addr.optString("residential"),
                addr.optString("road"),
                root.optString("name")
            ).mapNotNull { cleanToken(it) }.filter { isUsablePlaceToken(it) && !isBroadAdminName(it) }

            val localityFields = listOf(
                addr.optString("suburb"),
                addr.optString("municipality"),
                addr.optString("city_district"),
                addr.optString("town")
            ).mapNotNull { cleanToken(it) }.filter { isUsablePlaceToken(it) && !isBroadAdminName(it) }

            val broadFields = listOf(
                addr.optString("suburb"),
                addr.optString("town"),
                addr.optString("city"),
                addr.optString("county"),
                addr.optString("state_district"),
                addr.optString("state")
            ).mapNotNull { cleanToken(it) }.filter { isUsablePlaceToken(it) }

            val parentCandidates = listOf(
                addr.optString("city"),
                addr.optString("town"),
                addr.optString("county"),
                addr.optString("state_district"),
                addr.optString("state"),
                country
            ).mapNotNull { cleanToken(it) }.filter { isUsablePlaceToken(it) }

            val (spot, tier) = when {
                microFields.isNotEmpty() -> microFields.first() to GRANULARITY_MICRO
                localityFields.isNotEmpty() -> localityFields.first() to GRANULARITY_LOCALITY
                broadFields.isNotEmpty() -> broadFields.first() to GRANULARITY_BROAD
                else -> return null
            }

            val parent = parentCandidates.firstOrNull { !it.equals(spot, ignoreCase = true) }
                ?: parentCandidates.firstOrNull().orEmpty()

            ResolvedSpot(
                spotBn = spot,
                parentBn = parent,
                spotEn = spot,
                countryName = country.orEmpty(),
                granularityTier = tier
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractExactPlaceFromAddresses(addresses: List<Address>): ResolvedSpot? {
        if (addresses.isEmpty()) return null

        val microTokens = mutableListOf<String>()
        val localityTokens = mutableListOf<String>()
        val broadTokens = mutableListOf<String>()
        val parentTokens = mutableListOf<String>()
        var country = ""

        for (addr in addresses) {
            if (country.isBlank()) country = cleanToken(addr.countryName).orEmpty()

            val districtOrCity = setOfNotNull(
                cleanToken(addr.subAdminArea)?.lowercase(Locale.ROOT),
                cleanToken(addr.adminArea)?.lowercase(Locale.ROOT)
            )

            // Parse address line 0 for ultra-specific house/road/para/village segments first
            val line0 = cleanToken(addr.getAddressLine(0))
            if (!line0.isNullOrBlank()) {
                val parts = line0.split(",")
                    .mapNotNull { cleanToken(it) }
                    .filter { isUsablePlaceToken(it) && !isPureNumber(it) }
                val firstPart = parts.firstOrNull {
                    it.lowercase(Locale.ROOT) !in districtOrCity &&
                        !it.equals(country, ignoreCase = true) &&
                        !isBroadAdminName(it)
                }
                if (firstPart != null && firstPart !in microTokens) {
                    microTokens.add(firstPart)
                }
            }

            listOf(addr.thoroughfare, addr.subLocality, addr.featureName)
                .mapNotNull { cleanToken(it) }
                .filter {
                    isUsablePlaceToken(it) &&
                        !isPureNumber(it) &&
                        it.lowercase(Locale.ROOT) !in districtOrCity &&
                        !isBroadAdminName(it)
                }
                .forEach { tok -> if (tok !in microTokens) microTokens.add(tok) }

            listOf(addr.subLocality, addr.locality)
                .mapNotNull { cleanToken(it) }
                .filter {
                    isUsablePlaceToken(it) &&
                        !isPureNumber(it) &&
                        it.lowercase(Locale.ROOT) !in districtOrCity &&
                        !isBroadAdminName(it)
                }
                .forEach { tok -> if (tok !in localityTokens) localityTokens.add(tok) }

            listOf(addr.locality, addr.subAdminArea, addr.adminArea)
                .mapNotNull { cleanToken(it) }
                .filter { isUsablePlaceToken(it) && !isPureNumber(it) }
                .forEach { tok ->
                    if (tok !in broadTokens) broadTokens.add(tok)
                    if (tok !in parentTokens) parentTokens.add(tok)
                }

            cleanToken(addr.countryName)?.let {
                if (isUsablePlaceToken(it) && it !in parentTokens) parentTokens.add(it)
            }
        }

        val (spot, tier) = when {
            microTokens.isNotEmpty() -> microTokens.first() to GRANULARITY_MICRO
            localityTokens.isNotEmpty() -> localityTokens.first() to GRANULARITY_LOCALITY
            broadTokens.isNotEmpty() -> broadTokens.first() to GRANULARITY_BROAD
            else -> return null
        }

        val parent = parentTokens.firstOrNull { !it.equals(spot, ignoreCase = true) }
            ?: parentTokens.firstOrNull().orEmpty()

        return ResolvedSpot(
            spotBn = spot,
            parentBn = parent,
            spotEn = spot,
            countryName = country,
            granularityTier = tier
        )
    }

    /**
     * Merges two resolved candidates, ALWAYS letting a finer-grained (lower tier)
     * spot win over a broader district/thana/city fallback. Enriches missing parent/country metadata.
     */
    internal fun mergeResolvedSpots(primary: ResolvedSpot?, secondary: ResolvedSpot?): ResolvedSpot? {
        if (primary == null) return secondary
        if (secondary == null) return primary

        val primaryWins = when {
            primary.spotBn.isBlank() -> false
            secondary.spotBn.isBlank() -> true
            primary.granularityTier < secondary.granularityTier -> true
            secondary.granularityTier < primary.granularityTier -> false
            containsBengaliScript(primary.spotBn) && !containsBengaliScript(secondary.spotBn) -> true
            containsBengaliScript(secondary.spotBn) && !containsBengaliScript(primary.spotBn) -> false
            else -> true
        }

        val winner = if (primaryWins) primary else secondary
        val other = if (primaryWins) secondary else primary

        return ResolvedSpot(
            spotBn = winner.spotBn.ifBlank { other.spotBn },
            parentBn = winner.parentBn.ifBlank { other.parentBn },
            spotEn = winner.spotEn.ifBlank { other.spotEn },
            countryName = winner.countryName.ifBlank { other.countryName },
            granularityTier = winner.granularityTier
        )
    }

    private fun containsBengaliScript(text: String): Boolean =
        text.any { it in '\u0980'..'\u09FF' }

    internal fun isBroadAdminName(raw: String): Boolean {
        val lower = raw.trim().lowercase(Locale.ROOT)
        if (lower.isBlank()) return true
        return BROAD_ADMIN_SUFFIXES.any { suffix ->
            lower == suffix || lower.endsWith(" $suffix") || lower.endsWith("-$suffix")
        }
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
     * Unlimited global + Bangladesh place search used by both the Map Picker search bar
     * and the District Dialog online search. Combines:
     * 1. Instant offline catalog matches (Divisions, 64 Districts, ~500 Thanas, Global cities)
     * 2. Per-device Android Geocoder (zero shared rate limits across thousands of users)
     * 3. OSM Photon Search API (geo-biased, free, no API key)
     * 4. Open-Meteo Geocoding API (worldwide cities/villages, free, high throughput)
     */
    suspend fun searchPlacesUnlimited(
        context: Context,
        query: String,
        biasLat: Double? = null,
        biasLng: Double? = null
    ): List<District> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val qLower = cleanQuery.lowercase(Locale.ROOT)
        val offlineMatches = LocationData.allLocations.filter {
            it.nameBn.lowercase(Locale.ROOT).contains(qLower) ||
                it.nameEn.lowercase(Locale.ROOT).contains(qLower) ||
                it.parentBn.lowercase(Locale.ROOT).contains(qLower)
        }.take(10)

        val remoteResults = coroutineScope {
            val geocoderDeferred = async { searchWithAndroidGeocoder(context, cleanQuery) }
            val photonDeferred = async { searchWithPhotonApi(cleanQuery, biasLat, biasLng) }
            val openMeteoDeferred = async { searchWithOpenMeteoApi(cleanQuery) }

            geocoderDeferred.await() + photonDeferred.await() + openMeteoDeferred.await()
        }

        val combined = offlineMatches + remoteResults
        deduplicateDistricts(combined).take(25)
    }

    suspend fun searchOnline(context: Context, query: String): List<District> =
        searchPlacesUnlimited(context, query)

    private fun searchWithAndroidGeocoder(context: Context, query: String): List<District> {
        if (!Geocoder.isPresent()) return emptyList()
        val results = mutableListOf<District>()
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 12).orEmpty()
            val tzDefault = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)
            for (addr in addresses) {
                val name = cleanToken(addr.subLocality)
                    ?: cleanToken(addr.thoroughfare)
                    ?: cleanToken(addr.locality)
                    ?: cleanToken(addr.featureName)
                    ?: cleanToken(addr.subAdminArea)
                    ?: query
                val parent = listOfNotNull(
                    cleanToken(addr.locality),
                    cleanToken(addr.subAdminArea),
                    cleanToken(addr.adminArea),
                    cleanToken(addr.countryName)
                ).firstOrNull { !it.equals(name, ignoreCase = true) }.orEmpty()
                val country = cleanToken(addr.countryName).orEmpty()
                val lat = addr.latitude
                val lng = addr.longitude
                val isBd = (lat in 20.5..26.8 && lng in 88.0..92.8) ||
                    country.contains("Bangladesh", ignoreCase = true)
                results.add(
                    District(
                        nameEn = name,
                        nameBn = name,
                        divisionBn = parent,
                        lat = lat,
                        lng = lng,
                        offsetMinutes = 0,
                        timeZone = if (isBd) 6.0 else tzDefault,
                        type = if (isBd) LocationType.CUSTOM_GPS else LocationType.GLOBAL_CITY,
                        parentBn = parent.ifBlank { country },
                        method = inferCalculationMethod(lat, lng, country)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun searchWithPhotonApi(query: String, biasLat: Double?, biasLng: Double?): List<District> {
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val biasPart = if (biasLat != null && biasLng != null) {
                String.format(Locale.US, "&lat=%.4f&lon=%.4f", biasLat, biasLng)
            } else {
                ""
            }
            val urlStr = "https://photon.komoot.io/api/?q=$encoded&limit=15$biasPart"
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", APP_USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parsePhotonSearchJson(body)
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parsePhotonSearchJson(jsonStr: String): List<District> {
        if (jsonStr.isBlank()) return emptyList()
        val results = mutableListOf<District>()
        try {
            val root = JSONObject(jsonStr)
            val features = root.optJSONArray("features") ?: return emptyList()
            val tzDefault = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)
            for (i in 0 until features.length()) {
                val feat = features.optJSONObject(i) ?: continue
                val coords = feat.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val lng = coords.optDouble(0)
                val lat = coords.optDouble(1)
                if (lat.isNaN() || lng.isNaN()) continue

                val props = feat.optJSONObject("properties") ?: continue
                val name = cleanToken(props.optString("name"))
                    ?: cleanToken(props.optString("city"))
                    ?: cleanToken(props.optString("street"))
                    ?: continue
                val country = cleanToken(props.optString("country")).orEmpty()
                val parent = listOfNotNull(
                    cleanToken(props.optString("district")),
                    cleanToken(props.optString("city")),
                    cleanToken(props.optString("county")),
                    cleanToken(props.optString("state")),
                    country.takeIf { it.isNotBlank() }
                ).filter { !it.equals(name, ignoreCase = true) }
                    .distinct()
                    .take(2)
                    .joinToString(", ")

                val isBd = (lat in 20.5..26.8 && lng in 88.0..92.8) ||
                    country.contains("Bangladesh", ignoreCase = true)
                results.add(
                    District(
                        nameEn = name,
                        nameBn = name,
                        divisionBn = parent,
                        lat = lat,
                        lng = lng,
                        offsetMinutes = 0,
                        timeZone = if (isBd) 6.0 else tzDefault,
                        type = if (isBd) LocationType.CUSTOM_GPS else LocationType.GLOBAL_CITY,
                        parentBn = parent.ifBlank { country },
                        method = inferCalculationMethod(lat, lng, country)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun searchWithOpenMeteoApi(query: String): List<District> {
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=12&language=en&format=json"
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", APP_USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseOpenMeteoSearchJson(body)
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseOpenMeteoSearchJson(jsonStr: String): List<District> {
        if (jsonStr.isBlank()) return emptyList()
        val results = mutableListOf<District>()
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("results") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = cleanToken(item.optString("name")) ?: continue
                val lat = item.optDouble("latitude", Double.NaN)
                val lng = item.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue
                val admin2 = cleanToken(item.optString("admin2"))
                val admin1 = cleanToken(item.optString("admin1"))
                val country = cleanToken(item.optString("country")).orEmpty()
                val tzId = cleanToken(item.optString("timezone"))
                val tzHours = if (!tzId.isNullOrBlank()) {
                    TimeZone.getTimeZone(tzId).getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)
                } else {
                    TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (1000.0 * 60.0 * 60.0)
                }
                val parent = listOfNotNull(admin2, admin1, country.takeIf { it.isNotBlank() })
                    .filter { !it.equals(name, ignoreCase = true) }
                    .distinct()
                    .take(2)
                    .joinToString(", ")
                val isBd = (lat in 20.5..26.8 && lng in 88.0..92.8) ||
                    country.contains("Bangladesh", ignoreCase = true)
                results.add(
                    District(
                        nameEn = name,
                        nameBn = name,
                        divisionBn = parent,
                        lat = lat,
                        lng = lng,
                        offsetMinutes = 0,
                        timeZone = if (isBd) 6.0 else tzHours,
                        type = if (isBd) LocationType.CUSTOM_GPS else LocationType.GLOBAL_CITY,
                        parentBn = parent.ifBlank { country },
                        method = inferCalculationMethod(lat, lng, country)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun deduplicateDistricts(list: List<District>): List<District> {
        val unique = mutableListOf<District>()
        for (item in list) {
            val isDup = unique.any { existing ->
                val sameCoords = abs(existing.lat - item.lat) < 0.01 && abs(existing.lng - item.lng) < 0.01
                val sameName = existing.nameBn.equals(item.nameBn, ignoreCase = true) ||
                    existing.nameEn.equals(item.nameEn, ignoreCase = true)
                sameCoords && sameName
            }
            if (!isDup) unique.add(item)
        }
        return unique
    }
}
