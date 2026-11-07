package com.deenzstudios.waktusolatdz.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import com.deenzstudios.waktusolatdz.data.PrayerTimes
import com.deenzstudios.waktusolatdz.data.PrayerTimeSlot
import kotlinx.coroutines.delay
import java.util.Calendar

internal data class NextPrayerInfo(val slot: PrayerTimeSlot, val time: String, val secondsRemaining: Int)

internal fun minutesOfDay(time: String): Int {
    val parts = time.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

internal fun Calendar.minutesOfDay(): Int = get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)

internal fun nextPrayerInfo(times: PrayerTimes, clock: Calendar): NextPrayerInfo {
    val prayers = listOf(PrayerTimeSlot.SUBUH to times.fajr, PrayerTimeSlot.ZOHOR to times.dhuhr,
        PrayerTimeSlot.ASAR to times.asr, PrayerTimeSlot.MAGHRIB to times.maghrib, PrayerTimeSlot.ISYAK to times.isha)
    val now = clock.minutesOfDay()
    val target = prayers.firstOrNull { minutesOfDay(it.second) > now } ?: prayers.first()
    var diff = minutesOfDay(target.second) * 60 - (now * 60 + clock.get(Calendar.SECOND))
    if (diff <= 0) diff += 24 * 60 * 60
    return NextPrayerInfo(target.first, target.second, diff)
}

/** One clock shared by XML data binders; sample text stays in tools: attributes. */
@Composable
internal fun rememberPrayerClock(showSeconds: Boolean = false): Calendar {
    val preview = LocalInspectionMode.current
    var clock by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(preview, showSeconds) {
        if (preview) return@LaunchedEffect
        while (true) {
            delay(1_000 - System.currentTimeMillis() % 1_000)
            val latest = Calendar.getInstance()
            if ((showSeconds && latest.timeInMillis / 1_000 != clock.timeInMillis / 1_000) ||
                latest.minutesOfDay() != clock.minutesOfDay() ||
                latest.get(Calendar.DAY_OF_YEAR) != clock.get(Calendar.DAY_OF_YEAR)) clock = latest
        }
    }
    return clock
}
