package com.deenzstudios.waktusolatdz.data

/**
 * Waktu solat harian bagi satu zon JAKIM.
 * Semua medan masa dalam format "HH:mm" (24 jam).
 */
data class PrayerTimes(
    val zone: String,
    val date: String,
    val hijriDate: String,
    val day: String,
    val imsak: String,
    val fajr: String,
    val syuruk: String,
    val dhuha: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
)
