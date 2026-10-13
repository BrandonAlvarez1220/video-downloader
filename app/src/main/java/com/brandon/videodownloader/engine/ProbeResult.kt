package com.brandon.videodownloader.engine

import org.json.JSONArray
import org.json.JSONObject

/** Resultado de analizar una URL: o es un video, o es una lista de reproducción. */
sealed interface ProbeResult {

    data class Video(
        val url: String,
        val title: String?,
        val uploader: String?,
        val site: String?,
        val thumbnail: String?,
        val durationSec: Long?,
        /** Resoluciones disponibles, de mayor a menor (p. ej. [2160, 1080, 720]). */
        val heights: List<Int>,
        /** Tamaño estimado (video + mejor audio) por resolución, si el sitio lo informa. */
        val sizes: Map<Int, Long> = emptyMap(),
        val audioSize: Long? = null,
    ) : ProbeResult

    data class Playlist(
        val title: String?,
        val uploader: String?,
        val entries: List<Video>,
    ) : ProbeResult

    companion object {
        fun parse(json: JSONObject, originalUrl: String): ProbeResult {
            val entries = json.optJSONArray("entries")
            if (json.optString("_type") == "playlist" || entries != null) {
                val videos = entries.orEmpty().mapNotNull { e ->
                    val url = e.str("url") ?: e.str("webpage_url") ?: return@mapNotNull null
                    videoFrom(e, url)
                }
                return Playlist(json.str("title"), json.str("uploader"), videos)
            }
            return videoFrom(json, json.str("webpage_url") ?: originalUrl)
        }

        private fun videoFrom(o: JSONObject, url: String): Video {
            val formats = o.optJSONArray("formats").orEmpty()
            val videoFormats = formats.filter { f ->
                f.optString("vcodec", "none") != "none" && f.optInt("height", 0) > 0
            }
            val heights = videoFormats.map { it.optInt("height") }.distinct().sortedDescending()
            // Audio-only = vcodec "none" y acodec distinto de "none".
            val audioSize = formats
                .filter { it.optString("vcodec") == "none" && it.optString("acodec", "none") != "none" }
                .mapNotNull { it.bytes() }
                .maxOrNull()
            val sizes = heights.associateWith { h ->
                videoFormats.filter { it.optInt("height") == h }.mapNotNull { it.bytes() }.maxOrNull()
            }.filterValues { it != null }.mapValues { (_, v) -> v!! + (audioSize ?: 0L) }
            return Video(
                url = url,
                title = o.str("title"),
                uploader = o.str("uploader") ?: o.str("channel"),
                site = o.str("extractor_key") ?: o.str("ie_key"),
                thumbnail = o.str("thumbnail") ?: lastThumbnail(o),
                durationSec = o.optDouble("duration").takeIf { !it.isNaN() }?.toLong(),
                heights = heights,
                sizes = sizes,
                audioSize = audioSize,
            )
        }

        private fun lastThumbnail(o: JSONObject): String? =
            o.optJSONArray("thumbnails").orEmpty().lastOrNull()?.str("url")

        private fun JSONObject.bytes(): Long? {
            val exact = optLong("filesize", 0)
            val approx = optLong("filesize_approx", 0)
            return (if (exact > 0) exact else approx).takeIf { it > 0 }
        }

        /** org.json devuelve "null" (texto) o "" en vez de null; normalizamos. */
        private fun JSONObject.str(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONArray?.orEmpty(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
