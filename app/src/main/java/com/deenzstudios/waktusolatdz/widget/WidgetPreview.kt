package com.deenzstudios.waktusolatdz.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.ui.theme.BrownText
import com.deenzstudios.waktusolatdz.ui.theme.Cream
import com.deenzstudios.waktusolatdz.ui.theme.Gold300
import com.deenzstudios.waktusolatdz.ui.theme.Gold500
import com.deenzstudios.waktusolatdz.ui.theme.Gold800
import com.deenzstudios.waktusolatdz.ui.theme.Gold900
import com.deenzstudios.waktusolatdz.ui.theme.GoldTextMuted

/**
 * Pratonton reka bentuk widget 4x2 dalam Android Studio (tab Design).
 *
 * Widget sebenar menggunakan RemoteViews + widget_prayer.xml; pratonton ini
 * meniru susun atur yang sama supaya senang dinilai tanpa pasang di peranti.
 */
private data class PreviewPrayer(
    val label: String,
    val time: String,
    val fardu: Boolean = true,
)

private val PREVIEW_TIMES = listOf(
    PreviewPrayer("Imsak", "05:40", fardu = false),
    PreviewPrayer("Subuh", "05:50"),
    PreviewPrayer("Syuruk", "06:58", fardu = false),
    PreviewPrayer("Dhuha", "07:26", fardu = false),
    PreviewPrayer("Zohor", "13:01"),
    PreviewPrayer("Asar", "16:18"),
    PreviewPrayer("Maghrib", "19:01"),
    PreviewPrayer("Isyak", "20:11"),
)

@Preview(name = "4 · Widget 4x2", widthDp = 360, heightDp = 170, showBackground = true)
@Composable
private fun WidgetPreview() = WidgetPreviewFrame(active = "Maghrib")

@Preview(name = "4 · Widget 4x2 (Isyak)", widthDp = 360, heightDp = 170, showBackground = true)
@Composable
private fun WidgetPreviewIsyak() = WidgetPreviewFrame(active = "Isyak")

@Composable
private fun WidgetPreviewFrame(active: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDE7D6))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Cream.copy(alpha = 0.8f))
                .border(1.dp, Gold300, RoundedCornerShape(24.dp))
                .padding(8.dp),
        ) {
            // Nama app
            Text(
                text = "Waktu Solat DZ",
                modifier = Modifier.fillMaxWidth(),
                color = Gold900,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )

            // Tarikh Hijri & Masihi
            Text(
                text = "6 Rabiulakhir 1448H · 17 Oktober 2026",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                color = GoldTextMuted,
                fontSize = 8.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            // Solat seterusnya
            Text(
                text = "SOLAT SETERUSNYA",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                color = GoldTextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.12.em,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = "Isyak · 20:11",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),
                color = Gold900,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            // Jam semasa + cuaca & suhu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "16:36",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gold500)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Cream,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(16.dp))
                Image(
                    painter = painterResource(R.drawable.ic_weather_partly),
                    contentDescription = "Ikon cuaca",
                    modifier = Modifier.size(27.dp),
                    colorFilter = ColorFilter.tint(Gold800),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "31°C",
                    color = BrownText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            // Lapan waktu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                PREVIEW_TIMES.forEach { item ->
                    val highlighted = item.fardu && item.label == active
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = item.label,
                            color = if (highlighted) Gold900 else GoldTextMuted,
                            fontSize = 8.sp,
                            maxLines = 1,
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .then(
                                    if (highlighted) {
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Gold500)
                                            .padding(horizontal = 3.dp, vertical = 2.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Text(
                                text = item.time,
                                color = if (highlighted) Cream else BrownText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Ikon lokasi & zon
            Image(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = "Ikon lokasi",
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(11.dp)
                    .align(Alignment.CenterHorizontally),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Text(
                text = "WLY01 · Wilayah Persekutuan",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),
                color = Gold800,
                fontSize = 8.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
