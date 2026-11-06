package com.deenzstudios.waktusolatdz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deenzstudios.waktusolatdz.data.LocationDetector
import com.deenzstudios.waktusolatdz.data.NotificationPreferences
import com.deenzstudios.waktusolatdz.data.NotificationSettings
import com.deenzstudios.waktusolatdz.data.PrayerCache
import com.deenzstudios.waktusolatdz.data.PrayerRepository
import com.deenzstudios.waktusolatdz.data.PrayerTimeSlot
import com.deenzstudios.waktusolatdz.data.PrayerTimes
import com.deenzstudios.waktusolatdz.data.PrayerZone
import com.deenzstudios.waktusolatdz.data.ZonePreferences
import com.deenzstudios.waktusolatdz.notification.IslamicEventAlarmScheduler
import com.deenzstudios.waktusolatdz.notification.PrayerAlarmScheduler
import com.deenzstudios.waktusolatdz.widget.PrayerWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PrayerUiState {
    data object Loading : PrayerUiState
    data class Success(val times: PrayerTimes) : PrayerUiState
    data class Error(val message: String) : PrayerUiState
}

sealed interface LocationUiState {
    data object Idle : LocationUiState
    data object Detecting : LocationUiState
    data class Detected(val zone: PrayerZone) : LocationUiState
    data class Failed(val message: String) : LocationUiState
}

/** Status penjadualan notifikasi (untuk dipaparkan dalam tab Tetapan). */
sealed interface NotificationUiState {
    data object Idle : NotificationUiState
    data object Scheduling : NotificationUiState
    data class Active(val count: Int) : NotificationUiState
    data class Failed(val message: String) : NotificationUiState
}

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PrayerRepository()
    private val preferences = ZonePreferences(application)
    private val locationDetector = LocationDetector(application)
    private val notificationPreferences = NotificationPreferences(application)
    private val alarmScheduler = PrayerAlarmScheduler(application)

    private val _zone = MutableStateFlow(preferences.selectedZone)

    /** Zon yang sedang digunakan (tersimpan dalam SharedPreferences). */
    val zone: StateFlow<String> = _zone.asStateFlow()

    private val _state = MutableStateFlow<PrayerUiState>(PrayerUiState.Loading)
    val state: StateFlow<PrayerUiState> = _state.asStateFlow()

    private val _location = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val location: StateFlow<LocationUiState> = _location.asStateFlow()

    private val _notifications = MutableStateFlow(notificationPreferences.settings())

    /** Pilihan notifikasi semasa (disimpan dalam SharedPreferences). */
    val notifications: StateFlow<NotificationSettings> = _notifications.asStateFlow()

    private val _notificationStatus = MutableStateFlow<NotificationUiState>(NotificationUiState.Idle)
    val notificationStatus: StateFlow<NotificationUiState> = _notificationStatus.asStateFlow()

    init {
        refresh()
        if (_notifications.value.enabled) scheduleNotifications()
    }

    fun refresh(zone: String = _zone.value) {
        viewModelScope.launch {
            _state.value = PrayerUiState.Loading
            _state.value = try {
                val times = repository.getToday(zone)
                val appContext = getApplication<Application>()
                PrayerCache(appContext).save(times)
                IslamicEventAlarmScheduler(appContext).schedule()
                PrayerWidgetProvider.updateAll(appContext)
                PrayerUiState.Success(times)
            } catch (e: Exception) {
                PrayerUiState.Error(e.message ?: "Gagal memuatkan waktu solat")
            }
        }
    }

    /** Tukar zon secara manual; pilihan ini tidak akan ditimpa oleh auto-detect. */
    fun selectZone(zone: String) {
        preferences.hasManualZone = true
        if (zone == _zone.value) return
        _zone.value = zone
        preferences.selectedZone = zone
        refresh(zone)
        rescheduleIfEnabled()
    }

    /** GPS is the zone selector; a saved manual choice must not disable detection. */
    fun shouldAutoDetect(): Boolean = true

    /** Kesan zon berdasarkan lokasi peranti. */
    fun detectZone() {
        if (!LocationDetector.hasPermission(getApplication())) {
            _location.value = LocationUiState.Failed("Kebenaran lokasi diperlukan")
            return
        }

        viewModelScope.launch {
            _location.value = LocationUiState.Detecting
            val detected = runCatching { locationDetector.detectZone() }.getOrNull()
            if (detected == null) {
                _location.value = LocationUiState.Failed(
                    "Lokasi tidak dapat dikesan. Pastikan GPS dihidupkan.",
                )
                return@launch
            }

            _location.value = LocationUiState.Detected(detected)
            // Lokasi automatik dijadikan pilihan utama semula selepas pengesanan berjaya.
            preferences.hasManualZone = false
            if (detected.code != _zone.value) {
                _zone.value = detected.code
                preferences.selectedZone = detected.code
                refresh(detected.code)
                rescheduleIfEnabled()
            }
        }
    }

    /** Hidupkan/matikan semua notifikasi waktu solat. */
    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.enabled = enabled
        _notifications.value = notificationPreferences.settings()

        if (enabled) {
            scheduleNotifications()
        } else {
            cancelNotifications()
        }
    }

    /** Peringatan 5 minit sebelum masuk waktu. */
    fun setReminderBefore(enabled: Boolean) {
        notificationPreferences.remindBefore = enabled
        _notifications.value = notificationPreferences.settings()
        rescheduleIfEnabled()
    }

    /** Notifikasi tepat pada waktu solat. */
    fun setNotifyOnTime(enabled: Boolean) {
        notificationPreferences.notifyOnTime = enabled
        _notifications.value = notificationPreferences.settings()
        rescheduleIfEnabled()
    }

    /** Hidupkan/matikan notifikasi bagi satu waktu (Imsak hingga Isyak). */
    fun setPrayerSlotEnabled(slot: PrayerTimeSlot, enabled: Boolean) {
        val current = notificationPreferences.slots
        notificationPreferences.slots =
            if (enabled) current + slot else current - slot
        _notifications.value = notificationPreferences.settings()
        rescheduleIfEnabled()
    }

    private fun rescheduleIfEnabled() {
        if (_notifications.value.enabled) scheduleNotifications()
    }

    private fun scheduleNotifications() {
        viewModelScope.launch {
            _notificationStatus.value = NotificationUiState.Scheduling
            _notificationStatus.value = try {
                val count = alarmScheduler.refresh()
                if (count > 0) {
                    NotificationUiState.Active(count)
                } else {
                    NotificationUiState.Failed("Tiada waktu solat untuk dijadualkan")
                }
            } catch (e: Exception) {
                NotificationUiState.Failed(
                    e.message ?: "Gagal menjadualkan notifikasi",
                )
            }
        }
    }

    private fun cancelNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            alarmScheduler.cancelAll()
            _notificationStatus.value = NotificationUiState.Idle
        }
    }
}
