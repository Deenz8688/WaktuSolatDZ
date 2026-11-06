package com.deenzstudios.waktusolatdz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deenzstudios.waktusolatdz.data.GeoPoint
import com.deenzstudios.waktusolatdz.data.LocationDetector
import com.deenzstudios.waktusolatdz.data.MosqueCache
import com.deenzstudios.waktusolatdz.data.MosqueRepository
import com.deenzstudios.waktusolatdz.data.NearbyMosque
import com.deenzstudios.waktusolatdz.data.PrayerZones
import com.deenzstudios.waktusolatdz.data.ZonePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface MosqueUiState {
    data object Loading : MosqueUiState

    /**
     * @param fromCache senarai terakhir daripada storan (pelayan peta gagal).
     */
    data class Success(
        val mosques: List<NearbyMosque>,
        val fromCache: Boolean = false,
    ) : MosqueUiState

    data class Error(val message: String) : MosqueUiState
}

/** Dari mana pusat carian masjid diambil. */
enum class MosqueOrigin {
    /** Lokasi sebenar peranti (kebenaran lokasi diberi). */
    Device,

    /** Titik tengah zon JAKIM — digunakan bila lokasi peranti tiada. */
    Zone,
}

/**
 * Carian masjid & surau berdekatan.
 *
 * Pusat carian: lokasi peranti jika kebenaran diberi, jika tidak titik tengah
 * zon waktu solat yang sedang dipilih — supaya tab ini tetap berguna.
 */
class MosqueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MosqueRepository()
    private val preferences = ZonePreferences(application)
    private val locationDetector = LocationDetector(application)
    private val cache = MosqueCache(application)

    private val _state = MutableStateFlow<MosqueUiState>(MosqueUiState.Loading)
    val state: StateFlow<MosqueUiState> = _state.asStateFlow()

    private val _center = MutableStateFlow(zoneCenter())
    val center: StateFlow<GeoPoint> = _center.asStateFlow()

    private val _origin = MutableStateFlow(MosqueOrigin.Zone)
    val origin: StateFlow<MosqueOrigin> = _origin.asStateFlow()

    private var loadedOnce = false
    private var loading = false

    /** Muat sekali sahaja — dipanggil setiap kali tab Masjid dibuka. */
    fun ensureLoaded() {
        if (!loadedOnce && !loading) load()
    }

    /** Muat semula dengan mengabaikan cache (butang "Muat semula"). */
    fun reload() {
        if (!loading) load(forceRefresh = true)
    }

    /** Zon bertukar — senarai lama tidak lagi relevan. */
    fun onZoneChanged() {
        if (loadedOnce) reload()
    }

    private fun load(forceRefresh: Boolean = loadedOnce) {
        loading = true
        viewModelScope.launch {
            _state.value = MosqueUiState.Loading
            try {
                val context = getApplication<Application>()
                // Jangan tunggu GPS terlalu lama — zon semasa sudah cukup sebagai pusat.
                val devicePoint = if (LocationDetector.hasPermission(context)) {
                    withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                        runCatching { locationDetector.currentCoordinates() }.getOrNull()
                    }
                } else {
                    null
                }
                val center = devicePoint ?: zoneCenter()
                _center.value = center
                _origin.value = if (devicePoint != null) MosqueOrigin.Device else MosqueOrigin.Zone

                val mosques = repository.nearby(center, forceRefresh = forceRefresh)
                cache.save(preferences.selectedZone, mosques)
                _state.value = MosqueUiState.Success(mosques)
                loadedOnce = true
            } catch (e: Exception) {
                // Rangkaian gagal — papar senarai terakhir yang tersimpan, jika ada.
                val saved = cache.load(preferences.selectedZone)
                _state.value = if (saved.isNullOrEmpty()) {
                    MosqueUiState.Error(e.message ?: "Gagal mencari masjid berdekatan")
                } else {
                    MosqueUiState.Success(saved, fromCache = true)
                }
            } finally {
                loading = false
            }
        }
    }

    private fun zoneCenter(): GeoPoint {
        val zone = PrayerZones.of(preferences.selectedZone)
        return if (zone != null) {
            GeoPoint(zone.latitude, zone.longitude)
        } else {
            GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        }
    }

    private companion object {
        /** Kuala Lumpur — lokasi lalai jika zon tidak dikenali. */
        const val DEFAULT_LATITUDE = 3.1390
        const val DEFAULT_LONGITUDE = 101.6869

        /** Had masa menunggu bacaan GPS sebelum guna pusat zon. */
        const val LOCATION_TIMEOUT_MS = 6_000L
    }
}
