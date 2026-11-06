package com.deenzstudios.waktusolatdz.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Kesan zon waktu solat berdasarkan lokasi peranti.
 *
 * Guna [LocationManager] sahaja (tiada Google Play Services): ambil lokasi terakhir
 * yang diketahui, jika tiada minta satu bacaan baharu, kemudian pilih zon JAKIM
 * yang paling hampir dengan koordinat tersebut.
 */
class LocationDetector(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    suspend fun detectZone(): PrayerZone? {
        val location = findLocation() ?: return null
        return PrayerZones.nearest(location.latitude, location.longitude)
    }

    /** Koordinat peranti semasa, digunakan untuk carian masjid berdekatan. */
    suspend fun currentCoordinates(): GeoPoint? {
        val location = findLocation() ?: return null
        return GeoPoint(location.latitude, location.longitude)
    }

    private suspend fun findLocation(): Location? {
        if (!hasPermission(appContext)) return null
        return try {
            lastKnownLocation() ?: requestSingleFix()
        } catch (e: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val manager = manager ?: return null
        val oldestAllowed = System.currentTimeMillis() - MAX_FIX_AGE_MS
        return PROVIDERS
            .mapNotNull { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) {
                        manager.getLastKnownLocation(provider)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            .filter { it.time >= oldestAllowed }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(): Location? = withTimeoutOrNull(FIX_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val manager = manager
                if (manager == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val provider = PROVIDERS.firstOrNull {
                    runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
                }
                if (provider == null) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit

                    @Deprecated("Diperlukan untuk API < 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }

                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
    }

    companion object {

        /** Lokasi kasar memadai untuk zon, tetapi lokasi tepat memberi bacaan lebih pantas. */
        fun hasPermission(context: Context): Boolean =
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ).any { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission,
                ) == PackageManager.PERMISSION_GRANTED
            }

        private val PROVIDERS = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        private const val FIX_TIMEOUT_MS = 12_000L

        /** Abaikan lokasi terakhir yang terlalu lama; minta bacaan baharu sebaliknya. */
        private const val MAX_FIX_AGE_MS = 5 * 60 * 1000L
    }
}
