package com.deenzstudios.waktusolatdz.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.*
import com.deenzstudios.waktusolatdz.databinding.*
import com.deenzstudios.waktusolatdz.ui.theme.WaktuSolatDZTheme
import java.util.Calendar
import kotlin.math.roundToInt

/** Child tags let each row keep its own unique IDs and literal labels in XML. */
internal class XmlPrayerRow(val root: View) {
    val time: TextView = root.findViewWithTag("prayer_time")
    val status: TextView = root.findViewWithTag("prayer_status")
}

internal fun prayerNameResource(slot: PrayerTimeSlot): Int = when (slot) {
    PrayerTimeSlot.IMSAK -> R.string.label_imsak
    PrayerTimeSlot.SUBUH -> R.string.prayer_fajr
    PrayerTimeSlot.SYURUK -> R.string.label_syuruk
    PrayerTimeSlot.DHUHA -> R.string.label_dhuha
    PrayerTimeSlot.ZOHOR -> R.string.prayer_dhuhr
    PrayerTimeSlot.ASAR -> R.string.prayer_asr
    PrayerTimeSlot.MAGHRIB -> R.string.prayer_maghrib
    PrayerTimeSlot.ISYAK -> R.string.prayer_isha
}

/** XML owns every tab's layout. Kotlin below only binds data and actions. */
@Composable
internal fun BottomMenuBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    AndroidView(
        factory = { context ->
            ViewBottomNavigationBinding.inflate(LayoutInflater.from(context)).root
        },
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        update = { root ->
            val binding = ViewBottomNavigationBinding.bind(root)
            listOf(binding.navHome to AppTab.Home, binding.navPrayerTime to AppTab.PrayerTime,
                binding.navQibla to AppTab.Qibla, binding.navMosque to AppTab.Mosque,
                binding.navSettings to AppTab.Settings).forEach { (view, tab) ->
                view.isActivated = selected == tab
                view.setOnClickListener { onSelect(tab) }
            }
        },
    )
}

@Composable
internal fun ScheduleTab(state: PrayerUiState, onRetry: () -> Unit) {
    val clock = rememberPrayerClock()
    AndroidView(factory = { ScheduleXmlView(it) }, modifier = Modifier.fillMaxSize(),
        update = { it.render(state, clock, onRetry) })
}

private class ScheduleXmlView(context: Context) : FrameLayout(context) {
    private val binding = ScreenScheduleBinding.inflate(LayoutInflater.from(context), this, true)
    fun render(state: PrayerUiState, clock: Calendar, onRetry: () -> Unit) {
        val success = state is PrayerUiState.Success
        binding.status.isVisible = !success
        binding.scheduleRows.isVisible = success
        binding.progress.isVisible = state is PrayerUiState.Loading
        binding.retry.isVisible = state is PrayerUiState.Error
        binding.message.isVisible = state is PrayerUiState.Error
        binding.message.text = (state as? PrayerUiState.Error)?.message.orEmpty()
        binding.title.setText(if (state is PrayerUiState.Error) R.string.home_error_heading else R.string.home_loading)
        binding.retry.setOnClickListener { onRetry() }
        if (state !is PrayerUiState.Success) return
        val times = state.times
        binding.date.text = context.getString(R.string.ui_schedule_date,
            times.day, formatGregorianDate(times.date), times.zone)
        val next = nextPrayerInfo(times, clock)
        val rows = listOf(binding.prayerImsak, binding.prayerSubuh, binding.prayerSyuruk,
            binding.prayerDhuha, binding.prayerZohor, binding.prayerAsar, binding.prayerMaghrib, binding.prayerIsyak).map(::XmlPrayerRow)
        PrayerTimeSlot.entries.zip(rows).forEach { (slot, row) ->
            row.root.isActivated = slot == next.slot && minutesOfDay(times.isha) > clock.minutesOfDay()
            row.time.text = slot.timeOf(times).orEmpty()
            row.status.isVisible = false
        }
    }
}

@Composable
internal fun QiblaTab(zone: String) {
    AndroidView(factory = { QiblaXmlView(it) }, modifier = Modifier.fillMaxSize(), update = { it.render(zone) })
}

private class QiblaXmlView(context: Context) : FrameLayout(context) {
    private val binding = ScreenQiblaBinding.inflate(LayoutInflater.from(context), this, true)
    init {
        binding.compass.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }
    fun render(zone: String) {
        val info = PrayerZones.of(zone)
        val bearing = info?.let { qiblaBearing(it.latitude, it.longitude) } ?: 0f
        val hasCompass = context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        binding.location.text = info?.let { context.getString(R.string.ui_join_details, it.state, it.district) } ?: zone
        binding.zone.text = zone
        binding.bearing.text = context.getString(R.string.xml_bearing, bearing.roundToInt())
        binding.compass.setContent {
            val azimuth = rememberCompassAzimuth()
            SideEffect {
                binding.reading.text = when {
                    !hasCompass -> context.getString(R.string.ui_no_compass)
                    azimuth == null -> context.getString(R.string.ui_waiting_compass)
                    else -> context.getString(R.string.xml_compass_reading, azimuth.roundToInt())
                }
            }
            QiblaDial(bearing, azimuth)
        }
    }
}

@Composable
internal fun MosqueTab(
    state: MosqueUiState, center: GeoPoint, origin: MosqueOrigin, zone: String,
    onRefresh: () -> Unit, onUseMyLocation: () -> Unit,
) {
    AndroidView(factory = { MosqueXmlView(it) }, modifier = Modifier.fillMaxSize(),
        update = { it.render(state, center, origin, zone, onRefresh, onUseMyLocation) })
}

private class MosqueXmlView(context: Context) : FrameLayout(context) {
    private val binding = ScreenMosqueBinding.inflate(LayoutInflater.from(context), this, true)
    private var selectedId: Long? = null
    init {
        binding.map.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }
    fun render(state: MosqueUiState, center: GeoPoint, origin: MosqueOrigin, zone: String,
        onRefresh: () -> Unit, onUseMyLocation: () -> Unit) {
        val info = PrayerZones.of(zone)
        binding.location.text = info?.let { context.getString(R.string.ui_join_details, it.state, it.district) } ?: zone
        binding.origin.text = if (origin == MosqueOrigin.Device) context.getString(R.string.ui_device_location)
            else context.getString(R.string.ui_zone_center, zone)
        binding.myLocation.setOnClickListener { onUseMyLocation() }
        binding.refresh.setOnClickListener { onRefresh() }
        binding.progress.isVisible = state is MosqueUiState.Loading
        val mosques = (state as? MosqueUiState.Success)?.mosques.orEmpty()
        if (mosques.none { it.id == selectedId }) selectedId = null
        binding.listStatus.text = when (state) {
            MosqueUiState.Loading -> context.getString(R.string.ui_mosque_loading)
            is MosqueUiState.Error -> state.message
            is MosqueUiState.Success -> when {
                state.fromCache -> context.getString(R.string.ui_mosque_cached, mosques.size)
                mosques.isEmpty() -> context.getString(R.string.ui_mosque_empty)
                else -> context.getString(R.string.ui_mosque_count, mosques.size)
            }
        }
        val select: (NearbyMosque) -> Unit = { mosque ->
            selectedId = mosque.id
            render(state, center, origin, zone, onRefresh, onUseMyLocation)
        }
        binding.map.setContent {
            WaktuSolatDZTheme {
                MosqueMapCanvas(center, if (origin == MosqueOrigin.Device) center else null,
                    mosques, selectedId, select, Modifier.fillMaxSize())
            }
        }
        binding.mosqueItems.removeAllViews()
        mosques.forEachIndexed { index, mosque ->
            val item = ItemMosqueBinding.inflate(LayoutInflater.from(context), binding.mosqueItems, false)
            item.root.isActivated = mosque.id == selectedId
            item.name.text = context.getString(R.string.ui_numbered_name, index + 1, mosque.name)
            val distance = if (mosque.distanceKm < 1) context.getString(R.string.ui_meters, (mosque.distanceKm * 1000).roundToInt())
                else context.getString(R.string.ui_kilometers, mosque.distanceKm)
            val kind = context.getString(if (mosque.kind == PlaceKind.MASJID) R.string.ui_kind_mosque else R.string.ui_kind_surau)
            item.distance.text = context.getString(R.string.ui_join_details, kind, distance)
            item.root.setOnClickListener { select(mosque) }
            item.navigate.setOnClickListener { openMosque(mosque) }
            binding.mosqueItems.addView(item.root)
        }
    }
    private fun openMosque(mosque: NearbyMosque) {
        val point = "${mosque.point.latitude},${mosque.point.longitude}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point(${Uri.encode(mosque.name)})"))
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.openstreetmap.org/?mlat=${mosque.point.latitude}&mlon=${mosque.point.longitude}#map=18/${mosque.point.latitude}/${mosque.point.longitude}")))
            }
        }
    }
}

@Composable
internal fun SettingsTab(
    state: PrayerUiState, selectedZone: String, locationState: LocationUiState,
    onDetectLocation: () -> Unit,
    notifications: NotificationSettings, exactAlarmAllowed: Boolean,
    onToggleNotifications: (Boolean) -> Unit, onToggleReminder: (Boolean) -> Unit,
    onToggleOnTime: (Boolean) -> Unit, onToggleSlot: (PrayerTimeSlot, Boolean) -> Unit,
    onRequestExactAlarm: () -> Unit,
) {
    AndroidView(factory = { SettingsXmlView(it) }, modifier = Modifier.fillMaxSize(), update = {
        it.render(state, selectedZone, locationState, onDetectLocation, notifications,
            exactAlarmAllowed, onToggleNotifications, onToggleReminder, onToggleOnTime,
            onToggleSlot, onRequestExactAlarm)
    })
}

private class SettingsXmlView(context: Context) : FrameLayout(context) {
    private val binding = ScreenSettingsBinding.inflate(LayoutInflater.from(context), this, true)
    private val detectButtonText = binding.detectLocation.text
    private var enabled = false

    fun render(state: PrayerUiState, zone: String, location: LocationUiState,
        onDetect: () -> Unit, settings: NotificationSettings,
        exactAllowed: Boolean, onEnabled: (Boolean) -> Unit, onReminder: (Boolean) -> Unit,
        onTime: (Boolean) -> Unit, onSlot: (PrayerTimeSlot, Boolean) -> Unit, onExact: () -> Unit) {
        val info = PrayerZones.of(zone)
        binding.currentState.text = info?.state ?: zone
        binding.currentDistrict.text = context.getString(R.string.ui_join_details, info?.district.orEmpty(), zone)
        binding.dataStatus.text = when (state) {
            PrayerUiState.Loading -> context.getString(R.string.home_loading)
            is PrayerUiState.Success -> context.getString(R.string.ui_latest_data, state.times.date)
            is PrayerUiState.Error -> context.getString(R.string.ui_load_failed, state.message)
        }
        binding.detectLocation.isEnabled = location != LocationUiState.Detecting
        binding.detectLocation.text = if (location == LocationUiState.Detecting)
            context.getString(R.string.ui_detecting_location) else detectButtonText
        binding.detectLocation.setOnClickListener { onDetect() }
        binding.locationStatus.text = when (location) {
            LocationUiState.Idle -> ""
            LocationUiState.Detecting -> context.getString(R.string.ui_enable_location)
            is LocationUiState.Detected -> context.getString(R.string.ui_location_detected, location.zone.state, location.zone.code)
            is LocationUiState.Failed -> location.message
        }
        bindSwitch(binding.notificationsEnabled, settings.enabled, onEnabled)
        enabled = settings.enabled
        binding.notificationOptions.isVisible = enabled
        bindSwitch(binding.reminderBefore, settings.remindBefore, onReminder)
        bindSwitch(binding.notifyOnTime, settings.notifyOnTime, onTime)
        val switches = listOf(binding.slotImsak, binding.slotSubuh, binding.slotSyuruk, binding.slotDhuha,
            binding.slotZohor, binding.slotAsar, binding.slotMaghrib, binding.slotIsyak)
        PrayerTimeSlot.entries.zip(switches).forEach { (slot, switch) ->
            bindSwitch(switch, slot in settings.slots) { onSlot(slot, it) }
        }
        binding.exactAlarmPanel.isVisible = !exactAllowed
        binding.allowExactAlarm.setOnClickListener { onExact() }
        refreshBatteryAccess()
        binding.allowBattery.setOnClickListener {
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(request) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }
    }
    private fun bindSwitch(view: CompoundButton, checked: Boolean, action: (Boolean) -> Unit) {
        view.setOnCheckedChangeListener(null)
        view.isChecked = checked
        var restoring = false
        view.setOnCheckedChangeListener { _, value ->
            if (!restoring) {
                action(value)
                // Settings/permission state remains authoritative, including a
                // denied notification permission that leaves the value unchanged.
                restoring = true
                view.isChecked = checked
                restoring = false
            }
        }
    }
    private fun refreshBatteryAccess() {
        val exempt = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
        binding.batteryPanel.isVisible = enabled && !exempt
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshBatteryAccess()
    }
}
