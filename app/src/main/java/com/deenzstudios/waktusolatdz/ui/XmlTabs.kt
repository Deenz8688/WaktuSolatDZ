package com.deenzstudios.waktusolatdz.ui

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.PowerManager
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.ImageButton
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.notification.PrayerNotifier
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
        val fardSlots = listOf(PrayerTimeSlot.SUBUH, PrayerTimeSlot.ZOHOR, PrayerTimeSlot.ASAR,
            PrayerTimeSlot.MAGHRIB, PrayerTimeSlot.ISYAK)
        val current = fardSlots.lastOrNull { slot ->
            slot.timeOf(times)?.let { minutesOfDay(it) <= clock.minutesOfDay() } == true
        } ?: PrayerTimeSlot.ISYAK
        val rows = listOf(binding.prayerImsak, binding.prayerSubuh, binding.prayerSyuruk,
            binding.prayerDhuha, binding.prayerZohor, binding.prayerAsar, binding.prayerMaghrib, binding.prayerIsyak).map(::XmlPrayerRow)
        PrayerTimeSlot.entries.zip(rows).forEach { (slot, row) ->
            row.root.isActivated = slot == current
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
    private var previewPlayer: MediaPlayer? = null
    private var previewRingtone: Ringtone? = null
    private var previewButton: ImageButton? = null
    private val ringtoneCompletionCheck = object : Runnable {
        override fun run() {
            if (previewRingtone?.isPlaying == true) postDelayed(this, 250)
            else stopAzanPreview()
        }
    }
    private val previewLifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) stopAzanPreview()
    }

    init {
        val soundPreferences = NotificationPreferences(context)
        listOf(
            Triple(binding.previewFajr, PrayerTimeSlot.SUBUH, R.raw.azan_subuh),
            Triple(binding.previewDhuhr, PrayerTimeSlot.ZOHOR, R.raw.azan_mekah_audio),
            Triple(binding.previewAsr, PrayerTimeSlot.ASAR, R.raw.azan_mekah_audio),
            Triple(binding.previewMaghrib, PrayerTimeSlot.MAGHRIB, R.raw.azan_mekah_audio),
            Triple(binding.previewIsha, PrayerTimeSlot.ISYAK, R.raw.azan_mekah_audio),
        ).forEach { (button, slot, audio) ->
            button.setOnClickListener { toggleAzanPreview(button, audio, soundPreferences.soundMode(slot)) }
        }
        listOf(
            Triple(binding.soundFajr, PrayerTimeSlot.SUBUH, listOf("normal", "azan", "silent")),
            Triple(binding.soundDhuhr, PrayerTimeSlot.ZOHOR, listOf("normal", "azan", "silent")),
            Triple(binding.soundAsr, PrayerTimeSlot.ASAR, listOf("normal", "azan", "silent")),
            Triple(binding.soundMaghrib, PrayerTimeSlot.MAGHRIB, listOf("normal", "azan", "silent")),
            Triple(binding.soundIsha, PrayerTimeSlot.ISYAK, listOf("normal", "azan", "silent")),
        ).forEach { (spinner, slot, modes) ->
            spinner.setSelection(modes.indexOf(soundPreferences.soundMode(slot)).coerceAtLeast(0))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    modes.getOrNull(position)?.let { mode ->
                        if (soundPreferences.soundMode(slot) != mode) {
                            stopAzanPreview()
                            soundPreferences.setSoundMode(slot, mode)
                        }
                        val button = when (slot) {
                            PrayerTimeSlot.SUBUH -> binding.previewFajr
                            PrayerTimeSlot.ZOHOR -> binding.previewDhuhr
                            PrayerTimeSlot.ASAR -> binding.previewAsr
                            PrayerTimeSlot.MAGHRIB -> binding.previewMaghrib
                            else -> binding.previewIsha
                        }
                        button.isEnabled = mode != "silent"
                        button.alpha = if (button.isEnabled) 1f else 0.35f
                    }
                }
            }
        }
        val selectedLanguage = AppCompatDelegate.getApplicationLocales()[0]?.language
            ?: context.resources.configuration.locales[0].language
        binding.languageGroup.check(if (selectedLanguage == "en") R.id.language_english else R.id.language_malay)
        binding.languageGroup.setOnCheckedChangeListener { _, id ->
            val language = if (id == R.id.language_english) "en" else "ms"
            if (language != selectedLanguage) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
            }
        }
        binding.languageHeader.setOnClickListener {
            val expanded = !binding.languageContent.isVisible
            binding.languageContent.isVisible = expanded
            binding.languageArrow.rotation = if (expanded) 180f else 0f
            binding.languageHeader.contentDescription = context.getString(
                if (expanded) R.string.settings_language_collapse else R.string.settings_language_expand,
            )
        }
        binding.notificationHeader.setOnClickListener {
            val expanded = !binding.notificationContent.isVisible
            if (!expanded) stopAzanPreview()
            binding.notificationContent.isVisible = expanded
            binding.notificationArrow.rotation = if (expanded) 180f else 0f
            binding.notificationHeader.setContentDescription(context.getString(
                if (expanded) R.string.settings_notification_collapse
                else R.string.settings_notification_expand,
            ))
        }
        binding.studioCredit.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val credit = view as TextView
            val width = credit.paint.measureText(credit.text.toString()).coerceAtLeast(1f)
            credit.paint.shader = LinearGradient(
                0f, 0f, width, 0f,
                intArrayOf(
                    ContextCompat.getColor(context, R.color.home_hero_start),
                    ContextCompat.getColor(context, R.color.home_hero_center),
                    ContextCompat.getColor(context, R.color.home_hero_end),
                ),
                null, Shader.TileMode.CLAMP,
            )
            credit.invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(previewLifecycleObserver)
    }

    override fun onDetachedFromWindow() {
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(previewLifecycleObserver)
        stopAzanPreview()
        super.onDetachedFromWindow()
    }

    private fun toggleAzanPreview(button: ImageButton, audio: Int, mode: String) {
        if (previewButton === button) {
            stopAzanPreview()
            return
        }
        stopAzanPreview()
        if (mode == "silent") return
        if (mode == "normal") {
            playSystemSoundPreview(button)
            return
        }
        try {
            val descriptor = resources.openRawResourceFd(audio)
                ?: throw IllegalStateException("Audio unavailable")
            val media = MediaPlayer()
            previewPlayer = media
            previewButton = button
            button.setImageResource(R.drawable.ic_adhan_stop)
            button.contentDescription = context.getString(R.string.adhan_preview_stop)
            media.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            descriptor.use { media.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            media.setOnPreparedListener { it.start() }
            media.setOnCompletionListener { stopAzanPreview() }
            media.setOnErrorListener { _, _, _ ->
                stopAzanPreview()
                Toast.makeText(context, R.string.adhan_preview_failed, Toast.LENGTH_SHORT).show()
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            stopAzanPreview()
            Toast.makeText(context, R.string.adhan_preview_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSystemSoundPreview(button: ImageButton) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PrayerNotifier.ensureChannels(context)
                context.getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(PrayerNotifier.CHANNEL_ON_TIME)?.sound
            } else {
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
            }
            if (uri == null ||
                (uri == Settings.System.DEFAULT_NOTIFICATION_URI &&
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION) == null)
            ) {
                Toast.makeText(context, R.string.adhan_system_sound_silent, Toast.LENGTH_SHORT).show()
                return
            }
            val ringtone = RingtoneManager.getRingtone(context, uri)
                ?: throw IllegalStateException("System sound unavailable")
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            previewRingtone = ringtone
            previewButton = button
            button.setImageResource(R.drawable.ic_adhan_stop)
            button.contentDescription = context.getString(R.string.adhan_preview_stop)
            ringtone.play()
            postDelayed(ringtoneCompletionCheck, 500)
        } catch (_: Exception) {
            stopAzanPreview()
            Toast.makeText(context, R.string.adhan_preview_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAzanPreview() {
        removeCallbacks(ringtoneCompletionCheck)
        previewRingtone?.stop()
        previewRingtone = null
        previewPlayer?.release()
        previewPlayer = null
        previewButton?.apply {
            setImageResource(R.drawable.ic_adhan_play)
            contentDescription = context.getString(R.string.adhan_preview_play)
        }
        previewButton = null
    }

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
