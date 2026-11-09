package com.deenzstudios.waktusolatdz.data

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

/** Format a JAKIM HH:mm time using the phone's 12/24-hour preference. */
fun formatDisplayTime(context: Context, time: String): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return time
    if (hour !in 0..23 || minute !in 0..59) return time
    val date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    return DateFormat.getTimeFormat(context).format(date)
}
