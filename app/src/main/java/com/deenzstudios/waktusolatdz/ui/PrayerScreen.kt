package com.deenzstudios.waktusolatdz.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.GeoPoint
import com.deenzstudios.waktusolatdz.data.IslamicEvents
import com.deenzstudios.waktusolatdz.data.LocationDetector
import com.deenzstudios.waktusolatdz.data.NearbyMosque
import com.deenzstudios.waktusolatdz.data.NotificationSettings
import com.deenzstudios.waktusolatdz.data.PlaceKind
import com.deenzstudios.waktusolatdz.data.PrayerTimeSlot
import com.deenzstudios.waktusolatdz.data.PrayerTimes
import com.deenzstudios.waktusolatdz.data.PrayerZone
import com.deenzstudios.waktusolatdz.data.PrayerZones
import com.deenzstudios.waktusolatdz.data.UpcomingIslamicEvent
import com.deenzstudios.waktusolatdz.data.formatGregorianDate
import com.deenzstudios.waktusolatdz.data.formatHijriDate
import com.deenzstudios.waktusolatdz.data.upcomingIslamicEvents
import com.deenzstudios.waktusolatdz.notification.PrayerNotifier
import com.deenzstudios.waktusolatdz.ui.theme.BrownText
import com.deenzstudios.waktusolatdz.ui.theme.Cream
import com.deenzstudios.waktusolatdz.ui.theme.CreamSoft
import com.deenzstudios.waktusolatdz.ui.theme.Gold100
import com.deenzstudios.waktusolatdz.ui.theme.Gold300
import com.deenzstudios.waktusolatdz.ui.theme.Gold500
import com.deenzstudios.waktusolatdz.ui.theme.Gold700
import com.deenzstudios.waktusolatdz.ui.theme.Gold800
import com.deenzstudios.waktusolatdz.ui.theme.Gold900
import com.deenzstudios.waktusolatdz.ui.theme.GoldSoft
import com.deenzstudios.waktusolatdz.ui.theme.GoldTextMuted
import com.deenzstudios.waktusolatdz.ui.theme.IslamicFontFamily
import com.deenzstudios.waktusolatdz.ui.theme.WaktuSolatDZTheme
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

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
        IslamicBackground()

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

/* ---------------------------------------------------------------- latar */

@Composable
private fun IslamicBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFC9A227)),
    ) {
        // Gambar latar utama (full-bleed mengikut skrin).
        Image(
            painter = painterResource(R.drawable.bg_main),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Lapisan gelap halus di bahagian atas & bawah supaya teks emas kekal jelas.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        0.18f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.26f),
                    ),
                ),
        )
    }
}

/* ------------------------------------------------------------ tab utama */

/**
 * Jarak atas kandungan. Logo app kini berada di dalam kad (PNG logo),
 * jadi kandungan tidak perlu lagi bermula jauh ke bawah.
 */
internal val ScreenTopInset = 56.dp

@Composable
private fun HomeTab(state: PrayerUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = ScreenTopInset, bottom = 24.dp),
    ) {
        when (state) {
            PrayerUiState.Loading -> LoadingCard()

            is PrayerUiState.Error -> ErrorCard(state.message, onRetry)

            is PrayerUiState.Success -> {
                val times = state.times
                val clock = rememberClock()
                val next = remember(times, clock) { nextPrayer(times, clock.minutesOfDay()) }

                ArchPrayerCard(
                    next = next,
                    times = times,
                    clock = clock,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
                IslamicDatesCard(times)
            }
        }

        Spacer(Modifier.height(16.dp))
        Footer(state)
    }
}

@Composable
private fun ScheduleTab(state: PrayerUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = ScreenTopInset, bottom = 24.dp),
    ) {
        when (state) {
            PrayerUiState.Loading -> LoadingCard()

            is PrayerUiState.Error -> ErrorCard(state.message, onRetry)

            is PrayerUiState.Success -> {
                val clock = rememberClock()
                val next = remember(state.times, clock) {
                    nextPrayer(state.times, clock.minutesOfDay())
                }
                ScheduleCard(state.times, activeName = next.name)
            }
        }
    }
}

/* -------------------------------------------------------------- kad-kad */

/** Outline emas berkilat (gradien) — kesan metalik pada bingkai kad. */
private val GoldShineBorder = Brush.linearGradient(
    colors = listOf(
        Gold900,
        Gold500,
        Color(0xFFFFF6CE),
        Gold300,
        Gold700,
        Gold900,
    ),
)

@Composable
private fun CreamCard(
    modifier: Modifier = Modifier,
    background: Color = Cream,
    borderBrush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val frame = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(background)
    Column(
        modifier = if (borderBrush != null) {
            frame.border(3.dp, borderBrush, shape).padding(20.dp)
        } else {
            frame.border(1.dp, Gold100, shape).padding(20.dp)
        },
        content = content,
    )
}

@Composable
private fun NextPrayerCard(next: NextPrayer, times: PrayerTimes, clock: Calendar) {
    CreamCard {
        // Jam, tarikh Masihi & tarikh Hijrah — bertingkat, selari di tengah kad.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = String.format(
                    Locale.US,
                    "%02d:%02d",
                    clock.get(Calendar.HOUR_OF_DAY),
                    clock.get(Calendar.MINUTE),
                ),
                color = Gold900,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${formatGregorianDate(times.date)} · ${times.day}",
                color = Gold800,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatHijriDate(times.hijriDate),
                color = GoldTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(1.dp)
                    .background(Gold100),
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "SOLAT SETERUSNYA",
            modifier = Modifier.fillMaxWidth(),
            color = GoldTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = next.name,
                    color = Gold900,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = next.countdown,
                    color = GoldTextMuted,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = next.time,
                color = Gold800,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Imsak, Syuruk & Dhuha dipaparkan dalam kad jadual waktu solat.
    }
}

/**
 * Kad solat seterusnya di dalam bingkai gerbang (PNG).
 *
 * Kad ini tinggi (nisbah 0.697), jadi teks diletakkan dalam bahagian tengah
 * gerbang — jarak sisi 15% dan atas 30% daripada lebar kad. Nilai ini boleh
 * dilaraskan bila Din kata teks perlu naik/turun.
 */
@Composable
private fun ArchPrayerCard(
    next: NextPrayer,
    times: PrayerTimes,
    clock: Calendar,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth(0.97f)
            .aspectRatio(0.697f),
        contentAlignment = Alignment.Center,
    ) {
        val cardWidth = maxWidth
        Image(
            painter = painterResource(R.drawable.card),
            contentDescription = "Bingkai kad solat seterusnya",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-22).dp)
                .padding(
                    start = cardWidth * 0.15f,
                    end = cardWidth * 0.15f,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.logoapp),
                contentDescription = "Logo Waktu Solat DZ",
                modifier = Modifier
                    .fillMaxWidth(0.50f)
                    .offset(y = (-34).dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(10.dp))

            Text(
                text = String.format(
                    Locale.US,
                    "%02d:%02d",
                    clock.get(Calendar.HOUR_OF_DAY),
                    clock.get(Calendar.MINUTE),
                ),
                color = Color.White,
                fontFamily = IslamicFontFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${formatGregorianDate(times.date)} · ${times.day}",
                color = Color.White.copy(alpha = 0.92f),
                fontFamily = IslamicFontFamily,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = formatHijriDate(times.hijriDate),
                color = Color.White.copy(alpha = 0.86f),
                fontFamily = IslamicFontFamily,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.45f)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "SOLAT SETERUSNYA",
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.90f),
                fontFamily = IslamicFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = next.name,
                    color = Color.White,
                    fontFamily = IslamicFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color.White.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = next.time,
                    color = Color.White,
                    fontFamily = IslamicFontFamily,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = next.countdown,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = IslamicFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScheduleCard(times: PrayerTimes, activeName: String) {
    CreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_mosque),
                contentDescription = "Ikon masjid",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Jadual Waktu Solat Hari Ini",
                color = Gold900,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Zon ${times.zone}",
                color = GoldTextMuted,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        times.scheduleSlots().forEachIndexed { index, slot ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            PrayerRow(slot = slot, active = slot.name == activeName)
        }
    }
}

@Composable
private fun PrayerRow(slot: PrayerSlot, active: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    val brush = if (active) {
        Brush.horizontalGradient(listOf(Gold500, Gold800))
    } else {
        Brush.linearGradient(listOf(CreamSoft, CreamSoft))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mosque),
            contentDescription = "Ikon masjid",
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(if (active) Cream else Gold800),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = slot.name,
            color = when {
                active -> Cream
                !slot.isFard -> GoldTextMuted
                else -> BrownText
            },
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = slot.time,
            color = when {
                active -> Cream
                !slot.isFard -> GoldTextMuted
                else -> Gold900
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Kad tarikh penting Islam — menggantikan jadual waktu solat di tab Home
 * (jadual penuh kekal di tab Waktu Solat).
 */
@Composable
private fun IslamicDatesCard(times: PrayerTimes) {
    val events = remember(times.hijriDate, times.date) {
        upcomingIslamicEvents(times.hijriDate, times.date, count = IslamicEvents.size)
    }
    if (events.isEmpty()) return
    val scrollState = rememberScrollState()

    CreamCard(background = GoldSoft, borderBrush = GoldShineBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            Spacer(Modifier.width(8.dp))
            Text(
                text = "Tarikh Penting Islam",
                color = Gold900,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${times.hijriDate.substringBefore("-")}H",
                color = GoldTextMuted,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Senarai boleh ditatal di dalam kad (kad Home tak jadi terlalu panjang).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp)
                .verticalScroll(scrollState),
        ) {
            events.forEachIndexed { index, event ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                IslamicDateRow(event = event, highlight = index == 0)
            }
        }
    }
}

@Composable
private fun IslamicDateRow(event: UpcomingIslamicEvent, highlight: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    val brush = if (highlight) {
        Brush.horizontalGradient(listOf(Gold500, Gold800))
    } else {
        Brush.linearGradient(listOf(CreamSoft, CreamSoft))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.name,
                color = if (highlight) Cream else BrownText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${event.hijriLabel} · ${event.dateLabel}",
                color = if (highlight) Cream else GoldTextMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (event.daysAway) {
                0 -> "Hari ini"
                1 -> "Esok"
                else -> "${event.daysAway} hari"
            },
            color = if (highlight) Cream else Gold800,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* ------------------------------------------------------ loading / ralat */

@Composable
private fun LoadingCard() {
    CreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Gold800,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Memuatkan waktu solat…",
                color = BrownText,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    CreamCard {
        Text(
            text = "Gagal memuatkan",
            color = Gold900,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = message, color = BrownText, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text(text = "Cuba lagi", color = Gold800, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Footer(state: PrayerUiState) {
    val status = when (state) {
        is PrayerUiState.Success -> "Sumber: e-Solat JAKIM • ${state.times.date}"
        else -> "Sumber: e-Solat JAKIM"
    }
    Text(
        text = status,
        color = Gold100,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/* --------------------------------------------------------------- tetapan */

@Composable
private fun SettingsTab(
    state: PrayerUiState,
    selectedZone: String,
    locationState: LocationUiState,
    onDetectLocation: () -> Unit,
    onSelectZone: (String) -> Unit,
    notifications: NotificationSettings,
    exactAlarmAllowed: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onToggleOnTime: (Boolean) -> Unit,
    onToggleSlot: (PrayerTimeSlot, Boolean) -> Unit,
    onRequestExactAlarm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = ScreenTopInset, bottom = 24.dp),
    ) {
        CurrentZoneCard(state, selectedZone)
        Spacer(Modifier.height(16.dp))
        AutoDetectCard(locationState = locationState, onDetect = onDetectLocation)
        Spacer(Modifier.height(16.dp))
        ZonePickerCard(selectedZone = selectedZone, onSelectZone = onSelectZone)
        Spacer(Modifier.height(16.dp))
        NotificationCard(
            settings = notifications,
            exactAlarmAllowed = exactAlarmAllowed,
            onToggle = onToggleNotifications,
            onToggleReminder = onToggleReminder,
            onToggleOnTime = onToggleOnTime,
            onToggleSlot = onToggleSlot,
            onRequestExactAlarm = onRequestExactAlarm,
        )
        Spacer(Modifier.height(14.dp))

        Text(
            text = "Zon yang dipilih akan disimpan pada peranti ini.",
            color = Gold100,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        // Kredit pembangun di bahagian paling bawah tetapan.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Powered by",
                color = Gold100.copy(alpha = 0.85f),
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Deenz Studios",
                color = Cream,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
private fun CurrentZoneCard(state: PrayerUiState, zoneCode: String) {
    val zone = PrayerZones.of(zoneCode)
    CreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ZON WAKTU SOLAT",
                    color = GoldTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = zone?.state ?: "Zon tidak diketahui",
                    color = Gold900,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = zone?.district ?: zoneCode,
                    color = BrownText,
                    fontSize = 12.sp,
                )
            }
            ZoneCodeBadge(zoneCode)
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Gold100),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = zoneStatus(state),
            color = GoldTextMuted,
            fontSize = 11.sp,
        )
    }
}

private fun zoneStatus(state: PrayerUiState): String = when (state) {
    PrayerUiState.Loading -> "Memuatkan waktu solat…"
    is PrayerUiState.Error -> "Gagal memuatkan: ${state.message}"
    is PrayerUiState.Success -> "Data terkini: ${state.times.date}"
}

@Composable
private fun AutoDetectCard(locationState: LocationUiState, onDetect: () -> Unit) {
    val detecting = locationState == LocationUiState.Detecting
    CreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = "Ikon lokasi",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Lokasi Automatik",
                color = Gold900,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Kesan zon waktu solat berdasarkan lokasi peranti. Pilihan manual akan diutamakan.",
            color = BrownText,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Gold500, Gold800)))
                .clickable(enabled = !detecting, onClick = onDetect)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Cream,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (detecting) "Mengesan lokasi…" else "Kesan Kawasan Saya",
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        val message = locationMessage(locationState)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = if (locationState is LocationUiState.Failed) Gold900 else GoldTextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

private fun locationMessage(state: LocationUiState): String? = when (state) {
    LocationUiState.Idle -> null
    LocationUiState.Detecting -> "Pastikan lokasi peranti dihidupkan."
    is LocationUiState.Detected -> "Dikesan: ${state.zone.state} • ${state.zone.code}"
    is LocationUiState.Failed -> state.message
}

@Composable
private fun NotificationCard(
    settings: NotificationSettings,
    exactAlarmAllowed: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onToggleOnTime: (Boolean) -> Unit,
    onToggleSlot: (PrayerTimeSlot, Boolean) -> Unit,
    onRequestExactAlarm: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    // Compose preview (layoutlib) tiada servis sistem sebenar — langkau semakan perkakasan.
    val inPreview = LocalInspectionMode.current
    var batteryExempt by remember {
        mutableStateOf(if (inPreview) false else isBatteryExempt(context))
    }

    DisposableEffect(view, inPreview) {
        if (inPreview) {
            onDispose { }
        } else {
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) batteryExempt = isBatteryExempt(context)
            }
            view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
        }
    }

    val onRequestBatteryExemption: () -> Unit = {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(request) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    CreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = "Ikon notifikasi",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Notifikasi",
                color = Gold900,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Peringatan solat harian mengikut zon yang dipilih.",
            color = BrownText,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))

        NotificationSwitchRow(
            label = "Aktifkan notifikasi",
            checked = settings.enabled,
            onCheckedChange = onToggle,
        )

        if (settings.enabled) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Gold100),
            )
            Spacer(Modifier.height(8.dp))

            NotificationSwitchRow(
                label = "Peringatan 5 minit sebelum",
                checked = settings.remindBefore,
                onCheckedChange = onToggleReminder,
            )
            NotificationSwitchRow(
                label = "Masuk waktu solat",
                checked = settings.notifyOnTime,
                onCheckedChange = onToggleOnTime,
            )

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Gold100),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "WAKTU NOTIFIKASI",
                color = GoldTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            PrayerTimeSlot.entries.forEach { slot ->
                NotificationSwitchRow(
                    label = slot.label,
                    checked = slot in settings.slots,
                    onCheckedChange = { onToggleSlot(slot, it) },
                )
            }

            if (!exactAlarmAllowed) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notifikasi boleh lewat selagi \"Alarm & peringatan\" belum dibenarkan. Benarkan untuk peringatan 5 minit dan masuk waktu yang tepat.",
                        color = Gold900,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestExactAlarm) {
                        Text(
                            text = "Benarkan",
                            color = Gold800,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (!batteryExempt) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Benarkan app berjalan di latar belakang supaya notifikasi tetap masuk selepas app ditutup.",
                        color = Gold900,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRequestBatteryExemption) {
                        Text(
                            text = "Benarkan",
                            color = Gold800,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun NotificationSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = BrownText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cream,
                checkedTrackColor = Gold800,
                checkedBorderColor = Gold800,
                uncheckedThumbColor = Cream,
                uncheckedTrackColor = Gold100,
                uncheckedBorderColor = Gold300,
            ),
        )
    }
}

/** Semak kebenaran alarm tepat dan segarkan nilai selepas pengguna kembali dari Tetapan. */
@Composable
private fun rememberExactAlarmAccess(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    val inPreview = LocalInspectionMode.current
    var allowed by remember {
        mutableStateOf(if (inPreview) false else hasExactAlarmAccess(context))
    }

    DisposableEffect(view, inPreview) {
        if (inPreview) {
            onDispose { }
        } else {
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) allowed = hasExactAlarmAccess(context)
            }
            view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
        }
    }

    return allowed
}

private fun hasExactAlarmAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        // runCatching: layoutlib (Compose preview) tiada servis sebenar dan boleh membaling.
        runCatching {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        }.getOrDefault(false)

private fun isBatteryExempt(context: Context): Boolean =
    runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }.getOrDefault(false)

@Composable
private fun ZoneCodeBadge(code: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Gold100)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = code,
            color = Gold800,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ZonePickerCard(selectedZone: String, onSelectZone: (String) -> Unit) {
    val current = PrayerZones.of(selectedZone)
    var chosenState by rememberSaveable(selectedZone) {
        mutableStateOf(current?.state ?: PrayerZones.states.first())
    }

    CreamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_mosque),
                contentDescription = "Ikon masjid",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Pilih Zon",
                color = Gold900,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrayerZones.states.forEach { name ->
                StateChip(
                    name = name,
                    selected = name == chosenState,
                    onClick = { chosenState = name },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Gold100),
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = "DAERAH / KAWASAN",
            color = GoldTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(8.dp))

        PrayerZones.inState(chosenState).forEachIndexed { index, zone ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            ZoneRow(
                district = zone.district,
                code = zone.code,
                selected = zone.code == selectedZone,
                onClick = { onSelectZone(zone.code) },
            )
        }
    }
}

@Composable
private fun StateChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Gold800 else CreamSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = name,
            color = if (selected) Cream else BrownText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ZoneRow(
    district: String,
    code: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val brush = if (selected) {
        Brush.horizontalGradient(listOf(Gold500, Gold800))
    } else {
        Brush.linearGradient(listOf(CreamSoft, CreamSoft))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = district,
                color = if (selected) Cream else BrownText,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = if (selected) "Zon semasa • $code" else code,
                color = if (selected) Gold100 else GoldTextMuted,
                fontSize = 10.sp,
            )
        }
        if (selected) {
            Image(
                painter = painterResource(R.drawable.ic_crescent_star),
                contentDescription = "Zon dipilih",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* ------------------------------------------------------------ menu bawah */

@Composable
private fun BottomMenuBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cream)
            .border(1.dp, Gold100, shape)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomMenuItem(
            iconRes = R.drawable.ic_home,
            label = "Utama",
            selected = selected == AppTab.Home,
            onClick = { onSelect(AppTab.Home) },
        )
        BottomMenuItem(
            iconRes = R.drawable.ic_clock,
            label = "Waktu Solat",
            selected = selected == AppTab.PrayerTime,
            onClick = { onSelect(AppTab.PrayerTime) },
        )
        BottomMenuItem(
            iconRes = R.drawable.ic_compass,
            label = "Kiblat",
            selected = selected == AppTab.Qibla,
            onClick = { onSelect(AppTab.Qibla) },
        )
        BottomMenuItem(
            iconRes = R.drawable.ic_mosque,
            label = "Masjid",
            selected = selected == AppTab.Mosque,
            onClick = { onSelect(AppTab.Mosque) },
        )
        BottomMenuItem(
            iconRes = R.drawable.ic_settings,
            label = "Tetapan",
            selected = selected == AppTab.Settings,
            onClick = { onSelect(AppTab.Settings) },
        )
    }
}

@Composable
private fun RowScope.BottomMenuItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) Gold100 else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(if (selected) Gold800 else GoldTextMuted),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) Gold800 else GoldTextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/* -------------------------------------------------------------- logik masa */

private data class PrayerSlot(
    val name: String,
    val time: String,
    val minutes: Int,
    val isFard: Boolean = true,
)

private data class NextPrayer(val name: String, val time: String, val countdown: String)

/** Lima waktu solat fardu — digunakan untuk "solat seterusnya". */
private fun PrayerTimes.prayerSlots(): List<PrayerSlot> = listOf(
    PrayerSlot("Subuh", fajr, fajr.toMinutesOfDay()),
    PrayerSlot("Zohor", dhuhr, dhuhr.toMinutesOfDay()),
    PrayerSlot("Asar", asr, asr.toMinutesOfDay()),
    PrayerSlot("Maghrib", maghrib, maghrib.toMinutesOfDay()),
    PrayerSlot("Isyak", isha, isha.toMinutesOfDay()),
)

/** Semua waktu ikut urutan hari, termasuk imsak, syuruk & dhuha. */
private fun PrayerTimes.scheduleSlots(): List<PrayerSlot> = listOf(
    PrayerSlot("Imsak", imsak, imsak.toMinutesOfDay(), isFard = false),
    PrayerSlot("Subuh", fajr, fajr.toMinutesOfDay()),
    PrayerSlot("Syuruk", syuruk, syuruk.toMinutesOfDay(), isFard = false),
    PrayerSlot("Dhuha", dhuha, dhuha.toMinutesOfDay(), isFard = false),
    PrayerSlot("Zohor", dhuhr, dhuhr.toMinutesOfDay()),
    PrayerSlot("Asar", asr, asr.toMinutesOfDay()),
    PrayerSlot("Maghrib", maghrib, maghrib.toMinutesOfDay()),
    PrayerSlot("Isyak", isha, isha.toMinutesOfDay()),
)

private fun String.toMinutesOfDay(): Int {
    val parts = split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return hour * 60 + minute
}

/** Jam yang hidup: dikemas kini setiap kali minit bertukar (tidak setiap saat). */
@Composable
private fun rememberClock(): Calendar {
    val inPreview = LocalInspectionMode.current
    var clock by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(inPreview) {
        if (inPreview) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            val latest = Calendar.getInstance()
            if (latest.get(Calendar.HOUR_OF_DAY) != clock.get(Calendar.HOUR_OF_DAY) ||
                latest.get(Calendar.MINUTE) != clock.get(Calendar.MINUTE)
            ) {
                clock = latest
            }
        }
    }
    return clock
}

private fun Calendar.minutesOfDay(): Int =
    get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)

private fun nextPrayer(times: PrayerTimes, nowMinutes: Int): NextPrayer {
    val slots = times.prayerSlots()
    val target = slots.firstOrNull { it.minutes > nowMinutes }
        ?: slots.first().copy(
            minutes = slots.first().minutes + DAY_MINUTES,
            time = slots.first().time,
        )
    val diff = target.minutes - nowMinutes

    val countdown = when {
        diff >= 60 -> "${diff / 60} jam ${diff % 60} minit lagi"
        diff > 0 -> "$diff minit lagi"
        else -> "Sekarang"
    }
    return NextPrayer(target.name, target.time, countdown)
}

private const val DAY_MINUTES = 24 * 60

/* --------------------------------------------------------------- preview */

/** Data contoh untuk pratonton dalam Android Studio (tidak digunakan oleh app). */
private val PREVIEW_STATE = PrayerUiState.Success(
    PrayerTimes(
        zone = "WLY01",
        date = "17-Okt-2026",
        hijriDate = "1448-05-06",
        day = "Sabtu",
        imsak = "05:40",
        fajr = "05:50",
        syuruk = "06:58",
        dhuha = "07:26",
        dhuhr = "13:01",
        asr = "16:18",
        maghrib = "19:01",
        isha = "20:11",
    ),
)

private val PREVIEW_ZONE = PrayerZone(
    code = "WLY01",
    state = "Wilayah Persekutuan",
    district = "Kuala Lumpur, Putrajaya",
    latitude = 3.139,
    longitude = 101.6869,
)

/** Masjid contoh untuk pratonton tab Masjid. */
private val PREVIEW_MOSQUES = listOf(
    NearbyMosque(
        id = 1L,
        name = "Masjid Jamek Sultan Abdul Samad",
        kind = PlaceKind.MASJID,
        point = GeoPoint(3.1489, 101.6956),
        distanceKm = 0.4,
    ),
    NearbyMosque(
        id = 2L,
        name = "Masjid India",
        kind = PlaceKind.MASJID,
        point = GeoPoint(3.1517, 101.6963),
        distanceKm = 0.8,
    ),
    NearbyMosque(
        id = 3L,
        name = "Surau Kampung Baru",
        kind = PlaceKind.SURAU,
        point = GeoPoint(3.1612, 101.7050),
        distanceKm = 1.6,
    ),
)

@Preview(name = "1 · Home", widthDp = 411, heightDp = 891)
@Composable
private fun HomeTabPreview() = PreviewScreen(AppTab.Home)

@Preview(name = "2 · Waktu Solat", widthDp = 411, heightDp = 891)
@Composable
private fun ScheduleTabPreview() = PreviewScreen(AppTab.PrayerTime)

@Preview(name = "3 · Tetapan", widthDp = 411, heightDp = 891)
@Composable
private fun SettingsTabPreview() = PreviewScreen(AppTab.Settings)

@Preview(name = "4 · Kiblat", widthDp = 411, heightDp = 891)
@Composable
private fun QiblaTabPreview() = PreviewScreen(AppTab.Qibla)

@Preview(name = "5 · Masjid", widthDp = 411, heightDp = 891)
@Composable
private fun MosqueTabPreview() = PreviewScreen(AppTab.Mosque)

/** Tiru susun atur penuh (latar + bottom menu) tanpa ViewModel. */
@Composable
private fun PreviewScreen(tab: AppTab) {
    WaktuSolatDZTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            IslamicBackground()

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = { BottomMenuBar(selected = tab, onSelect = {}) },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    when (tab) {
                        AppTab.Home -> HomeTab(PREVIEW_STATE, onRetry = {})
                        AppTab.PrayerTime -> ScheduleTab(PREVIEW_STATE, onRetry = {})
                        AppTab.Qibla -> QiblaTab(PREVIEW_ZONE.code)
                        AppTab.Mosque -> MosqueTab(
                            state = MosqueUiState.Success(PREVIEW_MOSQUES),
                            center = GeoPoint(PREVIEW_ZONE.latitude, PREVIEW_ZONE.longitude),
                            origin = MosqueOrigin.Device,
                            zone = PREVIEW_ZONE.code,
                            onRefresh = {},
                            onUseMyLocation = {},
                        )
                        AppTab.Settings -> SettingsTab(
                            state = PREVIEW_STATE,
                            selectedZone = PREVIEW_ZONE.code,
                            locationState = LocationUiState.Detected(PREVIEW_ZONE),
                            onDetectLocation = {},
                            onSelectZone = {},
                            notifications = NotificationSettings(
                                enabled = true,
                                remindBefore = true,
                                notifyOnTime = true,
                            ),
                            exactAlarmAllowed = false,
                            onToggleNotifications = {},
                            onToggleReminder = {},
                            onToggleOnTime = {},
                            onToggleSlot = { _, _ -> },
                            onRequestExactAlarm = {},
                        )
                    }
                }
            }
        }
    }
}
