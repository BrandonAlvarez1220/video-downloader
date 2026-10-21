package com.brandon.videodownloader.download

/**
 * Elige un título legible entre varias fuentes (página, metadatos de yt-dlp, análisis previo).
 *
 * Por qué hace falta: cuando el video sale de un reproductor incrustado o de un enlace directo,
 * yt-dlp a veces reporta como "título" el nombre técnico del archivo: un hash, "videoplayback",
 * "master", etc. Eso no sirve para identificar el video en la biblioteca.
 */
object Titles {
    private val technicalNames = setOf(
        "video", "videoplayback", "index", "master", "playlist", "embed", "player",
        "download", "stream", "manifest", "media", "file", "watch",
    )

    fun looksReal(title: String?): Boolean {
        val t = title?.trim() ?: return false
        if (t.isEmpty() || t.startsWith("http")) return false
        if (t.lowercase().substringBeforeLast('.') in technicalNames) return false
        // Hash o ID: una sola "palabra" larga, con letras y varios dígitos (p. ej. "a8f3k29dx0q7").
        val compact = t.replace(Regex("[._-]"), "")
        if (' ' !in t && compact.length >= 10 && compact.all { it.isLetterOrDigit() } && compact.count { it.isDigit() } >= 3) {
            return false
        }
        return true
    }

    /** El primer candidato que parezca un título real; si ninguno, el primero no vacío. */
    fun best(vararg candidates: String?): String? =
        (candidates.firstOrNull { looksReal(it) } ?: candidates.firstOrNull { !it.isNullOrBlank() })?.trim()
}
