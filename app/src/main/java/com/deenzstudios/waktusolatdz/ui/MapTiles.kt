package com.deenzstudios.waktusolatdz.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.deenzstudios.waktusolatdz.data.MosqueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Satu jubin peta (256 px) pada aras zum tertentu.
 *
 * [x] boleh berada di luar julat 0..2^z (kanvas menyambung dunia di tepi),
 * jadi [MapTiles] membalutnya sebelum membentuk URL.
 */
internal data class TileKey(val x: Int, val y: Int, val z: Int)

/**
 * Pemuat jubin peta tanpa kunci API.
 *
 * Peta asas: jubin OpenStreetMap standard (dengan sandaran pelayan Jerman).
 * Jubin disimpan dalam cache memori supaya pan/zoom semula tidak memuat turun
 * semula. Sila ambil perhatian polisi penggunaan jubin OSM untuk aplikasi
 * yang diedarkan secara meluas.
 */
internal object MapTiles {

    private val cache = object : LruCache<TileKey, ImageBitmap>(MAX_TILES) {
        override fun sizeOf(key: TileKey, value: ImageBitmap): Int = 1
    }

    suspend fun load(key: TileKey): ImageBitmap? {
        cache.get(key)?.let { return it }
        val image = download(key) ?: return null
        cache.put(key, image)
        return image
    }

    private suspend fun download(key: TileKey): ImageBitmap? = withContext(Dispatchers.IO) {
        for (url in urls(key)) {
            val bytes = request(url) ?: continue
            val bitmap = runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull() ?: continue

            val loaded = runCatching { bitmap.asImageBitmap() }.getOrNull() ?: continue
            return@withContext loaded
        }
        null
    }

    private fun urls(key: TileKey): List<String> {
        val range = 1 shl key.z
        if (key.y !in 0 until range) return emptyList()
        val x = ((key.x % range) + range) % range
        return listOf(
            "https://tile.openstreetmap.org/${key.z}/$x/${key.y}.png",
            "https://tile.openstreetmap.de/${key.z}/$x/${key.y}.png",
        )
    }

    private fun request(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", MosqueRepository.USER_AGENT)
            setRequestProperty("Accept", "image/png,image/*")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} untuk $url")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private const val MAX_TILES = 96
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
}
