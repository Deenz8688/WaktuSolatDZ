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
import com.deenzstudios.waktusolatdz.ui.theme.BrownText
import com.deenzstudios.waktusolatdz.ui.theme.Cream
import com.deenzstudios.waktusolatdz.ui.theme.CreamSoft
import com.deenzstudios.waktusolatdz.ui.theme.Gold100
import com.deenzstudios.waktusolatdz.ui.theme.Gold300
import com.deenzstudios.waktusolatdz.ui.theme.Gold500
import com.deenzstudios.waktusolatdz.ui.theme.Gold800
import com.deenzstudios.waktusolatdz.ui.theme.Gold900
import com.deenzstudios.waktusolatdz.ui.theme.GoldTextMuted
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

/* ------------------------------------------------------- masjid & surau */

/**
 * Tab "Masjid": peta masjid terdekat + senarai masjid/surau.
 *
 * Peta dilukis sendiri dalam Compose menggunakan jubin OpenStreetMap/CARTO —
 * jadi tiada kunci API needed dan tema krim/emas kekal konsisten.
 */
@Composable
fun MosqueTab(
    state: MosqueUiState,
    center: GeoPoint,
    origin: MosqueOrigin,
    zone: String,
    onRefresh: () -> Unit,
    onUseMyLocation: () -> Unit,
) {
    val context = LocalContext.current
    val info = remember(zone) { PrayerZones.of(zone) }
    val mosques = (state as? MosqueUiState.Success)?.mosques.orEmpty()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Pilihan dibuang jika senarai baharu tiada lagi masjid tersebut.
    LaunchedEffect(mosques) {
        if (selectedId != null && mosques.none { it.id == selectedId }) selectedId = null
    }

    val openInMaps: (NearbyMosque) -> Unit = { mosque ->
        val lat = mosque.point.latitude
        val lon = mosque.point.longitude
        val label = Uri.encode(mosque.name)
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)"),
        )
        runCatching { context.startActivity(intent) }.onFailure {
            val web = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=18/$lat/$lon"
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web))) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = ScreenTopInset, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Masjid & Surau",
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
        Spacer(Modifier.height(18.dp))

        MapCard(
            center = center,
            origin = origin,
            zone = zone,
            state = state,
            mosques = mosques,
            selectedId = selectedId,
            onSelect = { selectedId = it.id },
            onUseMyLocation = onUseMyLocation,
        )

        Spacer(Modifier.height(16.dp))

        MosqueListCard(
            state = state,
            selectedId = selectedId,
            onSelect = { selectedId = it.id },
            onNavigate = openInMaps,
            onRefresh = onRefresh,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Data peta & masjid © OpenStreetMap contributors",
            color = Gold100,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/* ------------------------------------------------------------- kad peta */

@Composable
private fun MapCard(
    center: GeoPoint,
    origin: MosqueOrigin,
    zone: String,
    state: MosqueUiState,
    mosques: List<NearbyMosque>,
    selectedId: Long?,
    onSelect: (NearbyMosque) -> Unit,
    onUseMyLocation: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cream)
            .border(1.dp, Gold100, shape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_mosque),
                contentDescription = "Ikon masjid",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Peta Masjid Terdekat",
                color = Gold900,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            PillBadge(text = "${MosqueRepository.DEFAULT_RADIUS_M / 1000} km")
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Gold100, RoundedCornerShape(20.dp)),
        ) {
            MapView(
                searchCenter = center,
                userPoint = if (origin == MosqueOrigin.Device) center else null,
                mosques = mosques,
                selectedId = selectedId,
                onSelect = onSelect,
                modifier = Modifier.fillMaxSize(),
            )

            if (state is MosqueUiState.Loading) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Cream.copy(alpha = 0.94f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Gold800,
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Mencari masjid…",
                        color = BrownText,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = "Ikon lokasi",
                modifier = Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = when (origin) {
                    MosqueOrigin.Device -> "Menggunakan lokasi peranti"
                    MosqueOrigin.Zone -> "Pusat carian: zon $zone"
                },
                color = GoldTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUseMyLocation) {
                Text(
                    text = "Lokasi saya",
                    color = Gold800,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/* -------------------------------------------------------------- peta OSM */

private const val TILE_PX = 256f
private const val MIN_ZOOM = 12
private const val MAX_ZOOM = 18
private const val DEFAULT_ZOOM = 14
private const val FOCUS_ZOOM = 16

@Composable
private fun MapView(
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
        TextStyle(color = Cream, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
            drawRect(CREAM_MAP_BACKGROUND)

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
                    color = Gold500.copy(alpha = 0.20f),
                    radius = 15.dp.toPx(),
                    center = position,
                )
                drawCircle(color = Cream, radius = 8.dp.toPx(), center = position)
                drawCircle(color = Gold800, radius = 5.5.dp.toPx(), center = position)
            }
        }

        // Kawalan zum
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapButton(label = "+") {
                zoom = (zoom + 1).coerceAtMost(MAX_ZOOM)
            }
            MapButton(label = "−") {
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
                .background(Cream)
                .border(1.dp, Gold300, CircleShape)
                .clickable {
                    mapCenter = searchCenter
                    zoom = DEFAULT_ZOOM
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = "Kembali ke lokasi",
                modifier = Modifier.size(17.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
        }

        Text(
            text = "© OpenStreetMap contributors",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Cream.copy(alpha = 0.90f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            color = GoldTextMuted,
            fontSize = 8.sp,
        )
    }
}

private val CREAM_MAP_BACKGROUND = Color(0xFFF2ECDC)

private const val TAP_REACH_DP = 24f

/** Penanda bulat bernombor, selaras dengan senarai di bawah peta. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMosqueMarker(
    position: Offset,
    label: TextLayoutResult?,
    selected: Boolean,
) {
    val radius = if (selected) 13.dp.toPx() else 10.dp.toPx()

    drawCircle(
        color = Gold900.copy(alpha = 0.16f),
        radius = radius + 1.5.dp.toPx(),
        center = position + Offset(0f, 1.5.dp.toPx()),
    )
    drawCircle(color = if (selected) Gold900 else Gold800, radius = radius, center = position)
    drawCircle(
        color = Cream,
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
            .background(Cream)
            .border(1.dp, Gold300, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Gold800,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/* ----------------------------------------------------------- kad senarai */

@Composable
private fun MosqueListCard(
    state: MosqueUiState,
    selectedId: Long?,
    onSelect: (NearbyMosque) -> Unit,
    onNavigate: (NearbyMosque) -> Unit,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cream)
            .border(1.dp, Gold100, shape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_mosque),
                contentDescription = "Ikon masjid",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(Gold800),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Senarai Masjid & Surau",
                color = Gold900,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (state is MosqueUiState.Success) {
                PillBadge(text = "${state.mosques.size}")
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Disusun mengikut jarak dari pusat carian.",
            color = GoldTextMuted,
            fontSize = 11.sp,
        )
        if (state is MosqueUiState.Success && state.fromCache) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Data tersimpan — pelayan peta tidak dapat dihubungi. " +
                    "Ketuk \"Muat semula senarai\" untuk cuba lagi.",
                color = Gold900,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(12.dp))

        when (state) {
            MosqueUiState.Loading -> LoadingRows()

            is MosqueUiState.Error -> {
                Text(
                    text = "Gagal memuatkan",
                    color = Gold900,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = state.message, color = BrownText, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRefresh) {
                    Text(
                        text = "Cuba lagi",
                        color = Gold800,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            is MosqueUiState.Success -> {
                if (state.mosques.isEmpty()) {
                    Text(
                        text = "Tiada masjid atau surau ditemui dalam " +
                            "${MosqueRepository.DEFAULT_RADIUS_M / 1000} km. " +
                            "Cuba muat semula atau gerakkan pusat carian.",
                        color = BrownText,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onRefresh) {
                        Text(
                            text = "Muat semula",
                            color = Gold800,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        state.mosques.forEachIndexed { index, mosque ->
                            if (index > 0) Spacer(Modifier.height(6.dp))
                            MosqueRow(
                                index = index,
                                mosque = mosque,
                                selected = mosque.id == selectedId,
                                onSelect = { onSelect(mosque) },
                                onNavigate = { onNavigate(mosque) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Gold100),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = onRefresh) {
                            Text(
                                text = "Muat semula senarai",
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
}

@Composable
private fun LoadingRows() {
    CreamSoftRow(text = "Mencari masjid berdekatan…", progress = true)
    Spacer(Modifier.height(6.dp))
    CreamSoftRow(text = "Menyusun mengikut jarak…", progress = false)
}

@Composable
private fun CreamSoftRow(text: String, progress: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CreamSoft)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (progress) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Gold800,
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, color = GoldTextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun MosqueRow(
    index: Int,
    mosque: NearbyMosque,
    selected: Boolean,
    onSelect: () -> Unit,
    onNavigate: () -> Unit,
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
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) Cream.copy(alpha = 0.28f) else Gold100),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                color = if (selected) Cream else Gold800,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mosque.name,
                color = if (selected) Cream else BrownText,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${mosque.kind.label} · ${formatDistance(mosque.distanceKm)}",
                color = if (selected) Gold100 else GoldTextMuted,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (selected) Cream.copy(alpha = 0.18f) else Gold100)
                .clickable(onClick = onNavigate),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = "Buka ${mosque.name} dalam peta",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(if (selected) Cream else Gold800),
            )
        }
    }
}

@Composable
private fun PillBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Gold100)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = Gold800,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatDistance(distanceKm: Double): String =
    if (distanceKm < 1.0) {
        "${(distanceKm * 1000).roundToInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", distanceKm)
    }

/* ------------------------------------------------------------ matematik peta */

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

/* --------------------------------------------------------------- preview */

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

@Preview(name = "Masjid", widthDp = 411, heightDp = 891)
@Composable
private fun MosqueTabPreview() = WaktuSolatDZTheme {
    Box(modifier = Modifier.fillMaxSize().background(Gold500)) {
        MosqueTab(
            state = MosqueUiState.Success(PREVIEW_MOSQUES),
            center = GeoPoint(3.1390, 101.6869),
            origin = MosqueOrigin.Device,
            zone = "WLY01",
            onRefresh = {},
            onUseMyLocation = {},
        )
    }
}
