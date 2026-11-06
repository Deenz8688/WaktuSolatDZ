package com.deenzstudios.waktusolatdz.data

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Koordinat ringkas (latitud/longitud) untuk carian berdekatan. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {

    /** Jarak bulatan besar (haversine) ke titik lain, dalam kilometer. */
    fun distanceKmTo(other: GeoPoint): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * sin(dLon / 2).pow(2)
        return 2 * earthRadiusKm * asin(sqrt(a))
    }
}

/** Jenis rumah ibadah yang dipaparkan dalam senarai. */
enum class PlaceKind(val label: String) {
    MASJID("Masjid"),
    SURAU("Surau"),
}

/** Satu masjid/surau yang ditemui di sekitar pusat carian. */
data class NearbyMosque(
    val id: Long,
    val name: String,
    val kind: PlaceKind,
    val point: GeoPoint,
    val distanceKm: Double,
)

/**
 * Sumber data masjid & surau berdekatan daripada OpenStreetMap.
 *
 * Dua sumber digunakan:
 * 1. **Overpass** (pertanyaan radius penuh) — sumber utama, data paling lengkap.
 * 2. **Photon** (carian teks dengan kotak sempadan) — sandaran bila Overpass
 *    sibuk, yang memang kerap berlaku pada pelayan percuma.
 *
 * Tiada kunci API diperlukan. Hasil disimpan dalam cache memori supaya bertukar
 * tab tidak mencetuskan permintaan berulang.
 */
class MosqueRepository(
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {

    private val cache = mutableMapOf<String, CachedResult>()

    /**
     * Cari masjid/surau dalam radius tertentu, disusun dari yang paling hampir.
     *
     * @param forceRefresh abaikan cache (digunakan oleh butang "Muat semula").
     */
    suspend fun nearby(
        center: GeoPoint,
        radiusMeters: Int = DEFAULT_RADIUS_M,
        limit: Int = DEFAULT_LIMIT,
        forceRefresh: Boolean = false,
    ): List<NearbyMosque> = withContext(Dispatchers.IO) {
        val cacheKey = cacheKey(center, radiusMeters)
        if (!forceRefresh) {
            cache[cacheKey]
                ?.takeIf { System.currentTimeMillis() - it.timestamp < CACHE_TTL_MS }
                ?.let { return@withContext it.mosques }
        }

        // Overpass dahulu; jika gagal atau kosong, cuba Photon yang lebih ringan.
        val fromOverpass = runCatching { parse(fetch(buildQuery(center, radiusMeters)), center) }
            .getOrDefault(emptyList())
        val mosques = if (fromOverpass.isNotEmpty()) {
            fromOverpass
        } else {
            searchPhoton(center, radiusMeters)
        }.sortedBy { it.distanceKm }.take(limit)

        cache[cacheKey] = CachedResult(System.currentTimeMillis(), mosques)
        mosques
    }

    /**
     * Hantar pertanyaan ke semua cermin Overpass serentak dan ambil respons
     * pertama yang berjaya.
     *
     * Overpass kerap sibuk; berlumba mengelakkan pengguna menunggu setiap cermin
     * satu demi satu (boleh sampai seminit). Cermin yang kalah akan dibatalkan.
     */
    private suspend fun fetch(query: String): String = try {
        withTimeout(FETCH_TIMEOUT_MS) {
            coroutineScope {
                val pending = endpoints
                    .map { endpoint ->
                        async(Dispatchers.IO) {
                            runCatching { request(endpoint, query) }.getOrNull()
                        }
                    }
                    .toMutableList()

                try {
                    while (pending.isNotEmpty()) {
                        val finished = select<Deferred<String?>> {
                            pending.forEach { deferred -> deferred.onAwait { deferred } }
                        }
                        pending.remove(finished)
                        finished.await()?.let { body -> return@coroutineScope body }
                    }
                } finally {
                    pending.forEach { it.cancel() }
                }

                throw IOException("Tiada pelayan peta yang menjawab. Cuba lagi nanti.")
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw IOException("Carian masjid mengambil masa terlalu lama. Cuba lagi.")
    }

    /* ------------------------------------------------------------ rangkaian */

    private fun request(endpoint: String, query: String): String {
        val body = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Pelayan peta memulangkan HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = PHOTON_CONNECT_TIMEOUT_MS
            readTimeout = PHOTON_READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Pelayan peta memulangkan HTTP $code")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /* --------------------------------------------------------------- photon */

    /**
     * Sandaran bila Overpass gagal: cari "masjid" dan "surau" melalui Photon
     * (enjin carian OpenStreetMap) dalam kotak sempadan sekitar pusat carian.
     * Hasilnya diisih mengikut jarak sebenar sebelum dipulangkan.
     */
    private suspend fun searchPhoton(
        center: GeoPoint,
        radiusMeters: Int,
    ): List<NearbyMosque> = coroutineScope {
        val latDelta = radiusMeters / METERS_PER_DEGREE_LATITUDE
        val lonDelta = radiusMeters /
            (METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01))
        val bbox = String.format(
            Locale.US,
            "%.5f,%.5f,%.5f,%.5f",
            center.longitude - lonDelta,
            center.latitude - latDelta,
            center.longitude + lonDelta,
            center.latitude + latDelta,
        )

        val attempts = PHOTON_TERMS
            .map { term ->
                async(Dispatchers.IO) {
                    runCatching {
                        parsePhoton(
                            requestGet(photonUrl(term, bbox, center)),
                            center,
                            radiusMeters,
                        )
                    }.getOrNull()
                }
            }
            .awaitAll()

        if (attempts.all { it == null }) {
            throw IOException("Tidak dapat menghubungi pelayan peta. Cuba lagi nanti.")
        }

        val byName = LinkedHashMap<String, NearbyMosque>()
        attempts.filterNotNull().flatten().forEach { mosque ->
            val key = mosque.name.lowercase(Locale.US)
            val existing = byName[key]
            if (existing == null || mosque.distanceKm < existing.distanceKm) {
                byName[key] = mosque
            }
        }
        byName.values.sortedBy { it.distanceKm }
    }

    private fun photonUrl(term: String, bbox: String, center: GeoPoint): String = buildString {
        append("https://photon.komoot.io/api/?q=")
        append(URLEncoder.encode(term, Charsets.UTF_8.name()))
        append("&limit=").append(PHOTON_LIMIT)
        append("&bbox=").append(bbox)
        append("&osm_tag=amenity:place_of_worship")
        append("&lat=").append(String.format(Locale.US, "%.6f", center.latitude))
        append("&lon=").append(String.format(Locale.US, "%.6f", center.longitude))
    }

    private fun parsePhoton(
        body: String,
        center: GeoPoint,
        radiusMeters: Int,
    ): List<NearbyMosque> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        val maxDistanceKm = radiusMeters / 1000.0 * 1.2 // kotak sempadan lebih luas sedikit
        val mosques = mutableListOf<NearbyMosque>()

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties") ?: continue
            if (properties.optString("osm_key") != "amenity") continue
            if (properties.optString("osm_value") != "place_of_worship") continue

            val name = properties.optString("name").trim()
            if (name.length < MIN_NAME_LENGTH || name.isGenericMosqueName()) continue

            val coordinates = feature.optJSONObject("geometry")
                ?.optJSONArray("coordinates")
                ?: continue
            val longitude = coordinates.optDouble(0, Double.NaN)
            val latitude = coordinates.optDouble(1, Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) continue

            val point = GeoPoint(latitude, longitude)
            val distanceKm = center.distanceKmTo(point)
            if (distanceKm > maxDistanceKm) continue

            val typeCode = when (properties.optString("osm_type")) {
                "W" -> 2L
                "R" -> 3L
                else -> 1L
            }

            mosques += NearbyMosque(
                id = typeCode * ID_TYPE_STRIDE + properties.optLong("osm_id"),
                name = name,
                kind = placeKind(name),
                point = point,
                distanceKm = distanceKm,
            )
        }
        return mosques
    }

    /* ---------------------------------------------------------------- parse */

    private fun parse(body: String, center: GeoPoint): List<NearbyMosque> {
        val root = JSONObject(body)
        val elements = root.optJSONArray("elements") ?: return emptyList()

        // Way dan node kadang-kadang merujuk masjid yang sama — simpan yang terdekat.
        val byName = LinkedHashMap<String, NearbyMosque>()
        for (index in 0 until elements.length()) {
            val element = elements.optJSONObject(index) ?: continue
            val tags = element.optJSONObject("tags") ?: continue
            if (tags.optString("religion").let { it.isNotEmpty() && it != "muslim" }) continue
            if (tags.optString("amenity").let { it.isNotEmpty() && it != "place_of_worship" }) continue

            val name = tags.mosqueName() ?: continue
            if (name.isGenericMosqueName()) continue

            val point = element.centerPoint() ?: continue
            val distanceKm = center.distanceKmTo(point)
            val mosque = NearbyMosque(
                id = element.stableId(),
                name = name,
                kind = tags.placeKind(name),
                point = point,
                distanceKm = distanceKm,
            )

            val key = name.lowercase(Locale.US)
            val existing = byName[key]
            if (existing == null || distanceKm < existing.distanceKm) byName[key] = mosque
        }

        return byName.values.sortedBy { it.distanceKm }
    }

    /** Node ada lat/lon terus; way & relation hanya ada "center" bila `out center`. */
    private fun JSONObject.centerPoint(): GeoPoint? {
        val directLat = optDoubleOrNull("lat")
        val directLon = optDoubleOrNull("lon")
        if (directLat != null && directLon != null) return GeoPoint(directLat, directLon)

        val centerObject = optJSONObject("center") ?: return null
        val lat = centerObject.optDoubleOrNull("lat") ?: return null
        val lon = centerObject.optDoubleOrNull("lon") ?: return null
        return GeoPoint(lat, lon)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key)) return null
        val value = optDouble(key, Double.NaN)
        return if (value.isNaN()) null else value
    }

    /** Nama Inggeris/Melayu diutamakan; nama generik seperti "Surau" sahaja ditolak. */
    private fun JSONObject.mosqueName(): String? {
        for (key in NAME_KEYS) {
            val value = optString(key).trim()
            if (value.length >= MIN_NAME_LENGTH) return value
        }
        return null
    }

    private fun String.isGenericMosqueName(): Boolean =
        lowercase(Locale.US).trim() in GENERIC_NAMES

    private fun JSONObject.placeKind(name: String): PlaceKind =
        placeKind(name, optString("building"))

    private fun placeKind(name: String, building: String = ""): PlaceKind {
        if (building == "mosque") return PlaceKind.MASJID
        val lower = name.lowercase(Locale.US)
        return when {
            lower.contains("surau") ||
                lower.contains("musolla") ||
                lower.contains("musala") ||
                lower.contains("madrasah") -> PlaceKind.SURAU

            else -> PlaceKind.MASJID
        }
    }

    /** Id unik merentas jenis elemen (node/way/relation boleh berkongsi nombor). */
    private fun JSONObject.stableId(): Long {
        val typeCode = when (optString("type")) {
            "way" -> 2L
            "relation" -> 3L
            else -> 1L
        }
        return typeCode * ID_TYPE_STRIDE + optLong("id")
    }

    /* --------------------------------------------------------------- query */

    private fun buildQuery(center: GeoPoint, radiusMeters: Int): String {
        val lat = String.format(Locale.US, "%.6f", center.latitude)
        val lon = String.format(Locale.US, "%.6f", center.longitude)
        return buildString {
            append("[out:json][timeout:").append(TIMEOUT_SECONDS).append("];(")
            for (filter in PLACE_FILTERS) {
                append(filter).append("(around:").append(radiusMeters).append(',')
                    .append(lat).append(',').append(lon).append(");")
            }
            append(");out center ").append(MAX_ELEMENTS).append(';')
        }
    }

    private fun cacheKey(center: GeoPoint, radiusMeters: Int): String =
        String.format(
            Locale.US,
            "%.3f:%.3f:%d",
            center.latitude,
            center.longitude,
            radiusMeters,
        )

    private data class CachedResult(
        val timestamp: Long,
        val mosques: List<NearbyMosque>,
    )

    companion object {

        /** Radius carian lalai (meter). */
        const val DEFAULT_RADIUS_M = 5_000

        /** Had masjid yang dipaparkan supaya senarai kekal ringkas. */
        const val DEFAULT_LIMIT = 20

        const val USER_AGENT = "WaktuSolatDZ/1.0 (Android)"

        private val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
        )

        private val PLACE_FILTERS = listOf(
            "node[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]",
            "way[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]",
            "relation[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]",
            "way[\"building\"=\"mosque\"]",
        )

        private val NAME_KEYS = listOf("name:ms", "name", "name:en", "official_name", "alt_name")

        private val GENERIC_NAMES = setOf(
            "masjid",
            "surau",
            "musolla",
            "musala",
            "musholla",
            "madrasah",
            "surau musolla",
        )

        private const val MIN_NAME_LENGTH = 3
        private const val ID_TYPE_STRIDE = 1_000_000_000_000L
        private const val MAX_ELEMENTS = 80
        private const val TIMEOUT_SECONDS = 25

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000
        private const val FETCH_TIMEOUT_MS = 15_000L
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        /* Sandaran Photon */
        private val PHOTON_TERMS = listOf("masjid", "surau")
        private const val PHOTON_LIMIT = 25
        private const val PHOTON_CONNECT_TIMEOUT_MS = 8_000
        private const val PHOTON_READ_TIMEOUT_MS = 12_000
        private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
    }
}
