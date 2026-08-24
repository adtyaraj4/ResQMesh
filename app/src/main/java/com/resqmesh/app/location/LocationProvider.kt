package com.resqmesh.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class EmergencyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long
)

/**
 * Wraps the plain Android `LocationManager` (not Play Services'
 * FusedLocationProviderClient) deliberately — it needs no extra Gradle
 * dependency, works fully offline (GPS satellites don't need internet;
 * only NETWORK_PROVIDER would, and this tries GPS_PROVIDER first), and
 * keeps this increment buildable without resolving new libraries.
 *
 * Per spec: if location isn't available, the caller must NOT block the
 * emergency send — [getCurrentLocation] returns null rather than
 * throwing or hanging indefinitely, and the UI/packet layer is
 * responsible for showing "LOCATION UNAVAILABLE" and sending with
 * latitude/longitude = null rather than refusing to send.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission") // caller is required to have checked permission first
    suspend fun getCurrentLocation(timeoutMillis: Long = 5000L): EmergencyLocation? {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // Fast path: a recent last-known fix, if one exists, avoids waiting
        // for a new GPS lock at all (important for "don't block the SOS").
        val lastKnown = bestLastKnownLocation(locationManager)
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < RECENT_FIX_MAX_AGE_MILLIS) {
            return lastKnown.toEmergencyLocation()
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return lastKnown?.toEmergencyLocation() // no active provider — fall back to stale fix or null

        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!resumed) {
                        resumed = true
                        locationManager.removeUpdates(this)
                        continuation.resume(location.toEmergencyLocation())
                    }
                }
            }

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }

            try {
                locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (!resumed) {
                    resumed = true
                    continuation.resume(null)
                }
                return@suspendCancellableCoroutine
            }

            // Timeout: don't let a GPS lock delay hold up an emergency send.
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (!resumed) {
                    resumed = true
                    locationManager.removeUpdates(listener)
                    continuation.resume(lastKnown?.toEmergencyLocation())
                }
            }, timeoutMillis)
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(locationManager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull {
            try {
                locationManager.getLastKnownLocation(it)
            } catch (e: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }
    }

    private fun Location.toEmergencyLocation() = EmergencyLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        timestampMillis = time
    )

    companion object {
        private const val RECENT_FIX_MAX_AGE_MILLIS = 2 * 60 * 1000L // 2 minutes
    }
}
