package com.deenzstudios.waktusolatdz.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deenzstudios.waktusolatdz.data.PrayerZones
import com.deenzstudios.waktusolatdz.ui.theme.BrownText
import com.deenzstudios.waktusolatdz.ui.theme.Cream
import com.deenzstudios.waktusolatdz.ui.theme.CreamSoft
import com.deenzstudios.waktusolatdz.ui.theme.Gold100
import com.deenzstudios.waktusolatdz.ui.theme.Gold300
import com.deenzstudios.waktusolatdz.ui.theme.Gold700
import com.deenzstudios.waktusolatdz.ui.theme.Gold800
import com.deenzstudios.waktusolatdz.ui.theme.Gold900
import com.deenzstudios.waktusolatdz.ui.theme.GoldTextMuted
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/* --------------------------------------------------------------- kiblat */

/** Koordinat Kaabah, Masjidil Haram. */
private const val KAABA_LATITUDE = 21.4225
private const val KAABA_LONGITUDE = 39.8262

@Composable
fun QiblaTab(zone: String) {
    val info = remember(zone) { PrayerZones.of(zone) }
    val bearing = remember(info) {
        info?.let { qiblaBearing(it.latitude, it.longitude) } ?: 0f
    }
    val azimuth = rememberCompassAzimuth()
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val hasCompass = remember(context, inPreview) {
        inPreview || context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = ScreenTopInset, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Arah Kiblat",
            color = Cream,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = info?.let { "${it.state} • ${it.district}" } ?: "Zon $zone",
            color = Gold100,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))

        QiblaCard {
            QiblaDial(bearing = bearing, azimuth = azimuth)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Kiblat ${bearing.roundToInt()}° dari Utara",
                color = Gold900,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    !hasCompass ->
                        "Peranti ini tiada sensor kompas — guna darjah kiblat di atas."

                    azimuth == null -> "Menunggu sensor kompas…"
                    else -> "Arah kompas ${azimuth.roundToInt()}°"
                },
                color = BrownText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Zon ${info?.code ?: zone}",
                color = GoldTextMuted,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Kalau jarum tidak stabil, gerakkan telefon membentuk angka 8 untuk kalibrasi.",
            color = Gold100,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QiblaCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cream)
            .border(1.dp, Gold100, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun QiblaDial(bearing: Float, azimuth: Float?) {
    val heading = azimuth ?: 0f
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = Gold800,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // Saiz ikut jejari cakera supaya nisbah sama pada semua skrin.
        val unit = radius / 100f
        // Dial berpusing berlawanan dengan hala telefon supaya Utara kekal betul.
        val rot = -heading

        // Cakera cekung: gradien lembut dari tengah ke tepi.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(CreamSoft, Cream),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Gold300,
            radius = radius - 1.2f * unit,
            center = center,
            style = Stroke(width = 2.4f * unit),
        )
        drawCircle(
            color = Gold100,
            radius = radius * 0.86f,
            center = center,
            style = Stroke(width = 1.2f * unit),
        )

        for (degrees in 0 until 360 step 15) {
            val major = degrees % 90 == 0
            drawLine(
                color = if (major) Gold800 else Gold300,
                start = pointOnCircle(center, radius * 0.955f, degrees + rot),
                end = pointOnCircle(
                    center,
                    radius * if (major) 0.865f else 0.915f,
                    degrees + rot,
                ),
                strokeWidth = (if (major) 1.9f else 1f) * unit,
                cap = StrokeCap.Round,
            )
        }

        listOf(0 to "U", 90 to "T", 180 to "S", 270 to "B").forEach { (degrees, text) ->
            val layout = measurer.measure(text, labelStyle)
            val position = pointOnCircle(center, radius * 0.79f, degrees + rot)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    position.x - layout.size.width / 2f,
                    position.y - layout.size.height / 2f,
                ),
            )
        }

        // Jarum kiblat gaya kompas sebenar: bentuk berlian dua warna.
        val axis = bearing + rot
        val shoulder = radius * 0.066f
        val northTip = pointOnCircle(center, radius * 0.69f, axis)
        val southTip = pointOnCircle(center, radius * 0.49f, axis + 180f)
        val leftShoulder = pointOnCircle(center, shoulder, axis + 90f)
        val rightShoulder = pointOnCircle(center, shoulder, axis - 90f)

        fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            close()
        }

        val needle = Path().apply {
            moveTo(leftShoulder.x, leftShoulder.y)
            lineTo(northTip.x, northTip.y)
            lineTo(rightShoulder.x, rightShoulder.y)
            lineTo(southTip.x, southTip.y)
            close()
        }

        // Bayang lembut di bawah jarum — kesan timbul.
        translate(left = 1.6f * unit, top = 2.6f * unit) {
            drawPath(needle, color = Gold900.copy(alpha = 0.12f))
        }

        // Bahagian menghala kiblat: emas gelap (sisi terang) + emas terang.
        drawPath(triangle(center, leftShoulder, northTip), color = Gold900)
        drawPath(triangle(center, northTip, rightShoulder), color = Gold700)
        // Ekor: nada cerah supaya arah kiblat lebih menonjol.
        drawPath(triangle(center, leftShoulder, southTip), color = Gold300)
        drawPath(triangle(center, southTip, rightShoulder), color = Gold100)
        drawPath(needle, color = Gold800.copy(alpha = 0.5f), style = Stroke(width = 1f * unit))

        // Hab tengah: cakera kecil bertindih jarum seperti pin kompas.
        drawCircle(
            color = Gold900.copy(alpha = 0.16f),
            radius = radius * 0.056f,
            center = center + Offset(1f * unit, 1.6f * unit),
        )
        drawCircle(color = Cream, radius = radius * 0.056f, center = center)
        drawCircle(
            color = Gold700,
            radius = radius * 0.056f,
            center = center,
            style = Stroke(width = 1.7f * unit),
        )
        drawCircle(color = Gold900, radius = radius * 0.018f, center = center)

        // Penanda tetap: arah telefon dipegang.
        val marker = Path().apply {
            moveTo(center.x, center.y - radius + 1f * unit)
            lineTo(center.x - 3.6f * unit, center.y - radius + 8f * unit)
            lineTo(center.x + 3.6f * unit, center.y - radius + 8f * unit)
            close()
        }
        drawPath(marker, color = Gold900)
    }
}

/** Arah kompas telefon (0° = Utara). Null jika tiada bacaan/sensor. */
@Composable
private fun rememberCompassAzimuth(): Float? {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    var azimuth by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context, inPreview) {
        if (inPreview) {
            onDispose { }
        } else {
            val manager = context.getSystemService(SensorManager::class.java)
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (manager == null || sensor == null) {
                onDispose { }
            } else {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val matrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(matrix, orientation)
                        azimuth = normalizeDegrees(
                            Math.toDegrees(orientation[0].toDouble()).toFloat(),
                        )
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                onDispose { manager.unregisterListener(listener) }
            }
        }
    }

    return azimuth
}

/** Arah kiblat dari satu lokasi, dalam darjah dari Utara sebenar. */
private fun qiblaBearing(latitude: Double, longitude: Double): Float {
    val phi = Math.toRadians(latitude)
    val kaabaPhi = Math.toRadians(KAABA_LATITUDE)
    val deltaLongitude = Math.toRadians(KAABA_LONGITUDE - longitude)
    val y = sin(deltaLongitude)
    val x = cos(phi) * tan(kaabaPhi) - sin(phi) * cos(deltaLongitude)
    return normalizeDegrees(Math.toDegrees(atan2(y, x)).toFloat())
}

private fun normalizeDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

private fun pointOnCircle(center: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        x = center.x + (radius * sin(radians)).toFloat(),
        y = center.y - (radius * cos(radians)).toFloat(),
    )
}
