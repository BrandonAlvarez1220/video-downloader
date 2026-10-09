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
            val heights = formats
                .filter { f -> f.optString("vcodec", "none") != "none" && f.optInt("height", 0) > 0 }
                .map { it.optInt("height") }
                .distinct()
                .sortedDescending()
            return Video(
                url = url,
                title = o.str("title"),
                uploader = o.str("uploader") ?: o.str("channel"),
                site = o.str("extractor_key") ?: o.str("ie_key"),
                thumbnail = o.str("thumbnail") ?: lastThumbnail(o),
                durationSec = o.optDouble("duration").takeIf { !it.isNaN() }?.toLong(),
                heights = heights,
            )
        }

        private fun lastThumbnail(o: JSONObject): String? =
            o.optJSONArray("thumbnails").orEmpty().lastOrNull()?.str("url")

        /** org.json devuelve "null" (texto) o "" en vez de null; normalizamos. */
        private fun JSONObject.str(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONArray?.orEmpty(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
