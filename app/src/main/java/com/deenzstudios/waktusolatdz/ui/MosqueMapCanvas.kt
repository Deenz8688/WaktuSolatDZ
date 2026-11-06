package com.deenzstudios.waktusolatdz.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deenzstudios.waktusolatdz.R
import com.deenzstudios.waktusolatdz.data.GeoPoint
import com.deenzstudios.waktusolatdz.data.MosqueRepository
import com.deenzstudios.waktusolatdz.data.NearbyMosque
import com.deenzstudios.waktusolatdz.data.PlaceKind
import com.deenzstudios.waktusolatdz.data.PrayerZones
import com.deenzstudios.waktusolatdz.ui.theme.InkText
import com.deenzstudios.waktusolatdz.ui.theme.SurfaceWhite
import com.deenzstudios.waktusolatdz.ui.theme.SurfaceSoft
import com.deenzstudios.waktusolatdz.ui.theme.Purple100
import com.deenzstudios.waktusolatdz.ui.theme.Purple300
import com.deenzstudios.waktusolatdz.ui.theme.Purple500
import com.deenzstudios.waktusolatdz.ui.theme.Purple800
import com.deenzstudios.waktusolatdz.ui.theme.Purple900
import com.deenzstudios.waktusolatdz.ui.theme.PurpleTextMuted
import com.deenzstudios.waktusolatdz.ui.theme.WaktuSolatDZTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

private const val TILE_PX = 256f
private const val MIN_ZOOM = 12
private const val MAX_ZOOM = 18
private const val DEFAULT_ZOOM = 14
private const val FOCUS_ZOOM = 16

@Composable
internal fun MosqueMapCanvas(
    searchCenter: GeoPoint,
    userPoint: GeoPoint?,
    mosques: List<NearbyMosque>,
    selectedId: Long?,
    onSelect: (NearbyMosque) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val tiles = remember { mutableStateMapOf<TileKey, ImageBitmap>() }
    val requested = remember { mutableSetOf<TileKey>() }
    val semaphore = remember { Semaphore(4) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var mapCenter by remember { mutableStateOf(searchCenter) }
    var zoom by rememberSaveable { mutableIntStateOf(DEFAULT_ZOOM) }

    // Pusat carian baharu (zon bertukar / lokasi dikesan).
    LaunchedEffect(searchCenter) { mapCenter = searchCenter }

    // Fokus pada masjid yang dipilih dari senarai.
    val selected = remember(mosques, selectedId) { mosques.firstOrNull { it.id == selectedId } }
    LaunchedEffect(selected?.id) {
        val target = selected ?: return@LaunchedEffect
        mapCenter = target.point
        if (zoom < FOCUS_ZOOM) zoom = FOCUS_ZOOM
    }

    // Muat jubin yang kelihatan (di luar LaunchedEffect-key supaya pan pantas
    // tidak membatalkan muat turun yang sedang berjalan).
    LaunchedEffect(mapCenter, zoom, canvasSize, inPreview) {
        if (inPreview || canvasSize.width == 0) return@LaunchedEffect
        val visible = visibleTiles(mapCenter, zoom, canvasSize.width, canvasSize.height)
        visible.forEach { key ->
            if (tiles.containsKey(key) || !requested.add(key)) return@forEach
            scope.launch {
                try {
                    semaphore.withPermit { MapTiles.load(key) }?.let { tiles[key] = it }
                } finally {
                    requested.remove(key)
                }
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val markerStyle = remember {
        TextStyle(color = SurfaceWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
    val markerLabels = remember(measurer, mosques, markerStyle) {
        mosques.mapIndexed { index, _ -> measurer.measure("${index + 1}", markerStyle) }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(mosques, zoom, canvasSize) {
                    detectTapGestures { tap ->
                        val size = IntSize(
                            canvasSize.width.coerceAtLeast(1),
                            canvasSize.height.coerceAtLeast(1),
                        )
                        val hit = mosques.minByOrNull { mosque ->
                            (screenPosition(mosque.point, mapCenter, zoom, size) - tap).getDistance()
                        }
                        val reach = TAP_REACH_DP * density
                        if (hit != null &&
                            (screenPosition(hit.point, mapCenter, zoom, size) - tap)
                                .getDistance() <= reach
                        ) {
                            onSelect(hit)
                        }
                    }
                }
                .pointerInput(zoom) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        mapCenter = panCenter(mapCenter, zoom, drag.x, drag.y)
                    }
                },
        ) {
            drawRect(MAP_BACKGROUND)

            val origin = worldPixel(mapCenter, zoom) -
                Offset(size.width / 2f, size.height / 2f)
            for (key in visibleTiles(
                mapCenter,
                zoom,
                size.width.roundToInt(),
                size.height.roundToInt(),
            )) {
                val image = tiles[key] ?: continue
                drawImage(
                    image = image,
                    topLeft = Offset(key.x * TILE_PX - origin.x, key.y * TILE_PX - origin.y),
                )
            }

            // Penanda masjid — nombor sepadan dengan senarai di bawah.
            mosques.forEachIndexed { index, mosque ->
                if (mosque.id == selectedId) return@forEachIndexed
                drawMosqueMarker(
                    position = screenPosition(mosque.point, mapCenter, zoom, canvasSize),
                    label = markerLabels.getOrNull(index),
                    selected = false,
                )
            }

            selected?.let { mosque ->
                val index = mosques.indexOf(mosque)
                drawMosqueMarker(
                    position = screenPosition(mosque.point, mapCenter, zoom, canvasSize),
                    label = markerLabels.getOrNull(index),
                    selected = true,
                )
            }

            userPoint?.let { point ->
                val position = screenPosition(point, mapCenter, zoom, canvasSize)
                drawCircle(
                    color = Purple500.copy(alpha = 0.20f),
                    radius = 15.dp.toPx(),
                    center = position,
                )
                drawCircle(color = SurfaceWhite, radius = 8.dp.toPx(), center = position)
                drawCircle(color = Purple800, radius = 5.5.dp.toPx(), center = position)
            }
        }

        // Kawalan zum
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapButton(label = stringResource(R.string.ui_zoom_in)) {
                zoom = (zoom + 1).coerceAtMost(MAX_ZOOM)
            }
            MapButton(label = stringResource(R.string.ui_zoom_out)) {
                zoom = (zoom - 1).coerceAtLeast(MIN_ZOOM)
            }
        }

        // Kembali ke pusat carian
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(SurfaceWhite)
                .border(1.dp, Purple300, CircleShape)
                .clickable {
                    mapCenter = searchCenter
                    zoom = DEFAULT_ZOOM
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = stringResource(R.string.ui_map_recenter),
                modifier = Modifier.size(17.dp),
                colorFilter = ColorFilter.tint(Purple800),
            )
        }

        Text(
            text = stringResource(R.string.ui_map_attribution),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceWhite.copy(alpha = 0.90f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            color = PurpleTextMuted,
            fontSize = 8.sp,
        )
    }
}

private val MAP_BACKGROUND = Color(0xFFF1ECF8)

private const val TAP_REACH_DP = 24f

/** Penanda bulat bernombor, selaras dengan senarai di bawah peta. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMosqueMarker(
    position: Offset,
    label: TextLayoutResult?,
    selected: Boolean,
) {
    val radius = if (selected) 13.dp.toPx() else 10.dp.toPx()

    drawCircle(
        color = Purple900.copy(alpha = 0.16f),
        radius = radius + 1.5.dp.toPx(),
        center = position + Offset(0f, 1.5.dp.toPx()),
    )
    drawCircle(color = if (selected) Purple900 else Purple800, radius = radius, center = position)
    drawCircle(
        color = SurfaceWhite,
        radius = radius,
        center = position,
        style = Stroke(width = if (selected) 2.dp.toPx() else 1.4.dp.toPx()),
    )
    if (label != null) {
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                position.x - label.size.width / 2f,
                position.y - label.size.height / 2f,
            ),
        )
    }
}

@Composable
private fun MapButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(SurfaceWhite)
            .border(1.dp, Purple300, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Purple800,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}


/** Kedudukan piksel dunia (Web Mercator) bagi satu titik pada aras zum tertentu. */
private fun worldPixel(point: GeoPoint, zoom: Int): Offset {
    val scale = (1 shl zoom) * TILE_PX
    val x = (point.longitude + 180.0) / 360.0 * scale
    val latRad = Math.toRadians(point.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale
    return Offset(x.toFloat(), y.toFloat())
}

private const val MAX_LATITUDE = 85.05112878

/** Posisi skrin (piksel) bagi satu titik, relatif kepada pusat peta. */
private fun screenPosition(
    point: GeoPoint,
    mapCenter: GeoPoint,
    zoom: Int,
    size: IntSize,
): Offset {
    val centerPixel = worldPixel(mapCenter, zoom)
    val pointPixel = worldPixel(point, zoom)
    return Offset(
        pointPixel.x - centerPixel.x + size.width / 2f,
        pointPixel.y - centerPixel.y + size.height / 2f,
    )
}

/** Senarai jubin yang perlu dipaparkan untuk viewport semasa. */
private fun visibleTiles(
    mapCenter: GeoPoint,
    zoom: Int,
    widthPx: Int,
    heightPx: Int,
): List<TileKey> {
    if (widthPx <= 0 || heightPx <= 0) return emptyList()
    val centerPixel = worldPixel(mapCenter, zoom)
    val left = centerPixel.x - widthPx / 2f
    val top = centerPixel.y - heightPx / 2f
    val firstX = floor(left / TILE_PX).toInt()
    val lastX = floor((left + widthPx) / TILE_PX).toInt()
    val firstY = floor(top / TILE_PX).toInt()
    val lastY = floor((top + heightPx) / TILE_PX).toInt()
    val range = 1 shl zoom

    return buildList {
        for (x in firstX..lastX) {
            for (y in firstY..lastY) {
                if (y in 0 until range) add(TileKey(x = x, y = y, z = zoom))
            }
        }
    }
}

/** Kira pusat peta baharu selepas pengguna menyeret peta sejauh (dx, dy) piksel. */
private fun panCenter(mapCenter: GeoPoint, zoom: Int, dx: Float, dy: Float): GeoPoint {
    val centerPixel = worldPixel(mapCenter, zoom)
    val x = (centerPixel.x - dx) / TILE_PX
    val y = (centerPixel.y - dy) / TILE_PX
    val scale = (1 shl zoom).toDouble()

    val longitude = x / scale * 360.0 - 180.0
    val n = PI - 2.0 * PI * y / scale
    val latitude = Math.toDegrees(atan(sinh(n)))

    return GeoPoint(
        latitude = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE),
        longitude = ((longitude + 540.0) % 360.0) - 180.0,
    )
}
