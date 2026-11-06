package com.deenzstudios.waktusolatdz.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.deenzstudios.waktusolatdz.data.LocationDetector
import com.deenzstudios.waktusolatdz.notification.PrayerNotifier
import com.deenzstudios.waktusolatdz.R

// Navigation and permission wiring. All tab layouts are in res/layout/screen_*.xml.
enum class AppTab { Home, PrayerTime, Qibla, Mosque, Settings }

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun PrayerScreen(viewModel: PrayerViewModel, mosqueViewModel: MosqueViewModel) {
    val state by viewModel.state.collectAsState()
    val zone by viewModel.zone.collectAsState()
    val locationState by viewModel.location.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val mosqueState by mosqueViewModel.state.collectAsState()
    val mosqueCenter by mosqueViewModel.center.collectAsState()
    val mosqueOrigin by mosqueViewModel.origin.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }

    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            viewModel.detectZone()
            mosqueViewModel.reload()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    // Auto-detect sekali semasa app dibuka, selagi pengguna belum pilih zon sendiri.
    LaunchedEffect(Unit) {
        if (viewModel.shouldAutoDetect()) {
            if (LocationDetector.hasPermission(context)) {
                viewModel.detectZone()
            } else {
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
        }
    }

    val detectLocation = {
        if (LocationDetector.hasPermission(context)) {
            viewModel.detectZone()
        } else {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // Tab Masjid: muat sekali semasa dibuka, dan segarkan bila zon bertukar.
    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.Mosque) mosqueViewModel.ensureLoaded()
    }
    LaunchedEffect(zone) { mosqueViewModel.onZoneChanged() }

    // Butang "Lokasi saya" pada kad peta — minta kebenaran jika belum diberi.
    val useMyLocationForMosques = {
        if (LocationDetector.hasPermission(context)) {
            mosqueViewModel.reload()
        } else {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // Minta kebenaran POST_NOTIFICATIONS (Android 13+) sebelum menghidupkan notifikasi.
    val toggleNotifications: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> viewModel.setNotificationsEnabled(false)
            PrayerNotifier.canPost(context) -> viewModel.setNotificationsEnabled(true)
            else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exactAlarmAllowed = rememberExactAlarmAccess()
    val openExactAlarmSettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                BottomMenuBar(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selectedTab) {
                    AppTab.Home -> HomeTab(state, onRetry = { viewModel.refresh() })
                    AppTab.PrayerTime -> ScheduleTab(state, onRetry = { viewModel.refresh() })
                    AppTab.Qibla -> QiblaTab(zone)
                    AppTab.Mosque -> MosqueTab(
                        state = mosqueState,
                        center = mosqueCenter,
                        origin = mosqueOrigin,
                        zone = zone,
                        onRefresh = { mosqueViewModel.reload() },
                        onUseMyLocation = useMyLocationForMosques,
                    )
                    AppTab.Settings -> SettingsTab(
                        state = state,
                        selectedZone = zone,
                        locationState = locationState,
                        onDetectLocation = detectLocation,
                        onSelectZone = { viewModel.selectZone(it) },
                        notifications = notifications,
                        exactAlarmAllowed = exactAlarmAllowed,
                        onToggleNotifications = toggleNotifications,
                        onToggleReminder = { viewModel.setReminderBefore(it) },
                        onToggleOnTime = { viewModel.setNotifyOnTime(it) },
                        onToggleSlot = { slot, on -> viewModel.setPrayerSlotEnabled(slot, on) },
                        onRequestExactAlarm = openExactAlarmSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTab(state: PrayerUiState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val clock = rememberPrayerClock()
    val times = (state as? PrayerUiState.Success)?.times
    val next = remember(times, clock) { times?.let { nextPrayerInfo(it, clock) } }
    HomeXmlScreen(
        state = state,
        nextName = next?.let { context.getString(prayerNameResource(it.slot)) }.orEmpty(),
        nextTime = next?.time.orEmpty(),
        countdown = next?.let {
            if (it.minutesRemaining >= 60) context.getString(
                R.string.ui_countdown_hours, it.minutesRemaining / 60, it.minutesRemaining % 60,
            ) else context.getString(R.string.ui_countdown_minutes, it.minutesRemaining)
        }.orEmpty(),
        onRetry = onRetry,
    )
}
