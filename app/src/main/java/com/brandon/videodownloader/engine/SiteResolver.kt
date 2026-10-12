package com.brandon.videodownloader.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * Convierte una página de un agregador (animeflv, pelisplus…) en algo que yt-dlp sí entiende.
 *
 * El problema: en estos sitios la página NO trae el video. Trae un `<iframe>` que apunta al
 * reproductor real (StreamTape, FileMoon, Mp4Upload, DoodStream…), y yt-dlp no salta a los
 * iframes por su cuenta.
 *
 * La solución: leer la página, sacar del HTML (incluido el JavaScript ofuscado) la URL del
 * reproductor o, si se puede, el `.m3u8`/`.mp4` directo; y devolver esa URL junto con las
 * cabeceras (sobre todo `Referer`) que yt-dlp debe mandar para que el reproductor no responda 403.
 *
 * Es recursivo y limitado a [MAX_DEPTH] saltos, por si el iframe apunta a otra página intermedia.
 */
object SiteResolver {

    data class Resolved(
        /** URL que se le pasa a yt-dlp. */
        val mediaUrl: String,
        /** Página original que pegó el usuario (es la que se guarda en la cola). */
        val pageUrl: String,
        val title: String?,
        val siteId: String,
        val siteName: String,
        /** Cabeceras HTTP que yt-dlp debe enviar (Referer, User-Agent…). */
        val headers: Map<String, String>,
    )

    private const val MAX_DEPTH = 3

    /** ¿Este enlace es de un sitio que necesita resolución previa? */
    fun matches(url: String): Boolean = Sites.match(url) != null

    suspend fun resolve(url: String): Resolved = withContext(Dispatchers.IO) {
        val agg = Sites.match(url) ?: error("Sitio no soportado por el resolutor: $url")

        var current = url
        var referer: String? = null
        var html = ""
        var depth = 0

        while (depth++ < MAX_DEPTH) {
            val resp = Http.get(current, referer = referer)
            html = resp.body
            val base = resp.finalUrl
            val best = extractCandidates(html, base).firstOrNull() ?: break

            // Encontramos el reproductor (o el archivo directo): se lo pasamos a yt-dlp.
            if (isDirectMedia(best) || Sites.isYtDlpHost(best)) {
                return@withContext Resolved(
                    mediaUrl = best,
                    pageUrl = url,
                    title = pageTitle(html),
                    siteId = agg.id,
                    siteName = agg.name,
                    headers = headersFor(base),
                )
            }

            // El iframe apuntaba a otra página intermedia: seguimos un salto más.
            referer = base
            current = best
        }

        // No se pudo aislar el reproductor: le pasamos la página tal cual a yt-dlp
        // (su extractor genérico a veces encuentra el video) con el Referer de la página.
        Resolved(
            mediaUrl = url,
            pageUrl = url,
            title = pageTitle(html),
            siteId = agg.id,
            siteName = agg.name,
            headers = headersFor(url),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Extracción de candidatos del HTML
    // ---------------------------------------------------------------------------------------

    private val iframeRe = Regex(
        """<iframe[^>]*?(?:src|data-src|data-litespeed-src)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val sourceRe = Regex(
        """<(?:source|video|embed)[^>]*?(?:src|data-src)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    // `"code":"https:\/\/…"`, `file: '…'`, `sources:[{file:"…"}]`, `src = "…"` (JSON o JS)
    private val jsUrlRe = Regex(
        """(?:file|source|src|url|code|link|video)\s*[:=]\s*["']((?:https?:)?//[^"']{6,})["']""",
        RegexOption.IGNORE_CASE,
    )
    // URLs .m3u8/.mp4 sueltas dentro del HTML/JS.
    private val rawMediaRe = Regex(
        """(?:https?:)?//[^\s"'<>\\]+?\.(?:m3u8|mpd|mp4|webm|mkv|m4v)(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE,
    )
    // Atributos "data-" que algunos reproductores usan en vez de src.
    private val dataAttrRe = Regex(
        """data-(?:video|player|url|src|embed|file|iframe)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    // <meta property="og:video" content="…"> (y su gemelo con los atributos al revés).
    private val metaVideoA = Regex(
        """<meta[^>]+(?:property|name)=["'](?:og:video(?::(?:url|secure_url))?|twitter:player:stream)["'][^>]+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val metaVideoB = Regex(
        """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:og:video(?::(?:url|secure_url))?|twitter:player:stream)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val imageRe = Regex(
        """\.(?:jpg|jpeg|png|webp|gif|svg|ico|bmp|avif)(?:\?|#|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val mediaRe = Regex(
        """\.(?:m3u8|mpd|mp4|webm|mkv|m4v|mov|ts)(?:\?|#|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val embedPathRe = Regex(
        """/(?:embed|e|v|d|player|iframe|watch|video)/""",
        RegexOption.IGNORE_CASE,
    )

    private fun extractCandidates(html: String, baseUrl: String): List<String> {
        // 1) Desempaquetamos el JS ofuscado y desescapamos el HTML (\/ → /, &amp; → &…).
        val text = JsPacker.decodeAtobBlocks(JsPacker.unpack(html)) + "\n" + unescape(html)

        val raw = LinkedHashSet<String>()
        iframeRe.findAll(text).forEach { raw += it.groupValues[1] }
        sourceRe.findAll(text).forEach { raw += it.groupValues[1] }
        jsUrlRe.findAll(text).forEach { raw += it.groupValues[1] }
        dataAttrRe.findAll(text).forEach { raw += it.groupValues[1] }
        metaVideoA.findAll(text).forEach { raw += it.groupValues[1] }
        metaVideoB.findAll(text).forEach { raw += it.groupValues[1] }
        rawMediaRe.findAll(text).forEach { raw += it.value }

        return raw.asSequence()
            .mapNotNull { absolute(it, baseUrl) }
            .filter { looksLikeMedia(it) }
            .distinct()
            .sortedBy { rank(it) } // estable: dentro de cada categoría conserva el orden de aparición
            .toList()
    }

    private fun looksLikeMedia(url: String): Boolean {
        if (Sites.isBlocked(url)) return false
        if (imageRe.containsMatchIn(url)) return false
        if (mediaRe.containsMatchIn(url)) return true
        if (Sites.isYtDlpHost(url)) return true
        return embedPathRe.containsMatchIn(url)
    }

    private fun isDirectMedia(url: String): Boolean = mediaRe.containsMatchIn(url)

    /** Menor = mejor: 0 archivo directo, 1 reproductor conocido por yt-dlp, 2 otro. */
    private fun rank(url: String): Int = when {
        mediaRe.containsMatchIn(url) -> 0
        Sites.isYtDlpHost(url) -> 1
        else -> 2
    }

    // ---------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------

    private fun absolute(raw: String, baseUrl: String): String? {
        val u = unescape(raw).trim().trim('"', '\'').replace(" ", "%20")
        if (u.isBlank()) return null
        if (u.startsWith("data:") || u.startsWith("javascript:") ||
            u.startsWith("blob:") || u.startsWith("#")
        ) return null
        val full = when {
            u.startsWith("//") -> "https:$u"
            u.startsWith("http://") || u.startsWith("https://") -> u
            else -> runCatching { java.net.URI(baseUrl).resolve(u).toString() }.getOrNull() ?: return null
        }
        return full.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun unescape(s: String): String = s
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("&#47;", "/")
        .replace("&amp;", "&")

    private fun headersFor(referer: String): Map<String, String> = mapOf(
        "Referer" to referer,
        "User-Agent" to Http.USER_AGENT,
    )

    private val ogTitleA = Regex(
        """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val ogTitleB = Regex(
        """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']""",
        RegexOption.IGNORE_CASE,
    )
    private val h1Re = Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val titleTagRe = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private fun pageTitle(html: String): String? {
        val raw = ogTitleA.find(html)?.groupValues?.get(1)
            ?: ogTitleB.find(html)?.groupValues?.get(1)
            ?: h1Re.find(html)?.groupValues?.get(1)
            ?: titleTagRe.find(html)?.groupValues?.get(1)
        return raw?.let { stripTags(it) }?.takeIf { it.isNotBlank() }
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()
}

/**
 * Vuelca a las opciones de yt-dlp las cabeceras que necesita el reproductor resuelto.
 * `Referer` va con su opción propia; el resto, con `--add-header`.
 * Devuelve el propio request para poder seguir encadenando (`YoutubeDLRequest(...).addOption(...).applyResolvedHeaders(...)`).
 */
internal fun YoutubeDLRequest.applyResolvedHeaders(headers: Map<String, String>?): YoutubeDLRequest {
    headers?.forEach { (key, value) ->
        if (key.equals("Referer", ignoreCase = true)) addOption("--referer", value)
        else addOption("--add-header", "$key: $value")
    }
    return this
}
