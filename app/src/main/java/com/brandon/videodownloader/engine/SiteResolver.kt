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
        /** URLs candidatas en orden de preferencia (la primera es la mejor). */
        val mediaUrls: List<String>,
        /** Página original que pegó el usuario (es la que se guarda en la cola). */
        val pageUrl: String,
        val title: String?,
        val siteId: String,
        val siteName: String,
        /** Cabeceras HTTP que yt-dlp debe enviar (Referer, User-Agent…). */
        val headers: Map<String, String>,
    ) {
        val mediaUrl: String get() = mediaUrls.first()
    }

    private const val MAX_DEPTH = 3

    /** ¿El enlace pertenece a un agregador del catálogo ([Sites])? */
    fun matches(url: String): Boolean = Sites.match(url) != null

    /**
     * Resuelve SIEMPRE. Si no logra aislar el reproductor, devuelve la propia página
     * (para no romper el comportamiento anterior).
     */
    suspend fun resolve(url: String): Resolved = withContext(Dispatchers.IO) {
        val site = Sites.match(url)
        val found = search(url)
        Resolved(
            mediaUrls = found?.mediaUrls ?: listOf(url),
            pageUrl = url,
            title = found?.title,
            siteId = site?.id ?: hostName(url),
            siteName = site?.name ?: hostName(url),
            headers = headersFor(found?.referer ?: url),
        )
    }

    /**
     * Plan B GENERAL: intenta resolver CUALQUIER página, no solo las del catálogo.
     * Devuelve null si no encontró ningún reproductor/archivo (así se propaga el error original).
     */
    suspend fun tryResolve(url: String): Resolved? = withContext(Dispatchers.IO) {
        val found = search(url) ?: return@withContext null
        val site = Sites.match(url)
        Resolved(found.mediaUrls, url, found.title, site?.id ?: hostName(url), site?.name ?: hostName(url), headersFor(found.referer))
    }

    private data class Found(val mediaUrls: List<String>, val referer: String, val title: String?)

    /**
     * Lee la página (y los iframes intermedios) y devuelve los candidatos ordenados.
     * Se detiene en cuanto el mejor candidato es algo que yt-dlp sabe manejar o un archivo directo;
     * si no, sigue un salto más (hasta [MAX_DEPTH]).
     */
    private fun search(url: String): Found? {
        var current = url
        var referer: String? = null
        var fallback: Found? = null
        var depth = 0
        while (depth++ < MAX_DEPTH) {
            val resp = Http.get(current, referer = referer)
            val base = resp.finalUrl
            val candidates = extractCandidates(resp.body, base)
            if (candidates.isEmpty()) return fallback
            val found = Found(candidates, base, pageTitle(resp.body))
            // Varios servidores, o un archivo directo: esta es la página "final".
            if (candidates.size > 1 || isDirectFile(candidates.first())) return found
            // Un solo candidato que no es archivo directo: probablemente una página intermedia.
            if (fallback == null) fallback = found
            referer = base
            current = candidates.first()
        }
        return fallback
    }

    private fun isDirectFile(url: String): Boolean = mediaRe.containsMatchIn(url)

    private fun hostName(url: String): String = Sites.hostOf(url) ?: "Web"

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
    // URLs .m3u8/.mp4 sueltas dentro del HTML/JS. El lookahead evita cortar en `.mp4` de "mp4upload".
    private val rawMediaRe = Regex(
        """(?:https?:)?//[^\s"'<>\\]*?\.(?:m3u8|mpd|mp4|webm|mkv|m4v)(?:\?[^\s"'<>\\]*)?(?=["'\s<>\\]|$)""",
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
    // Algunos temas (WordPress) guardan la URL del reproductor en BASE64 dentro de un data-*:
    //   data-src="aHR0cHM6Ly9wbGF5ZXIu…"  →  https://player.…
    private val base64AttrRe = Regex(
        """data-(?:default-)?(?:src|url|video|player|file|embed|iframe)\s*=\s*["']([A-Za-z0-9+/_=-]{16,})["']""",
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
    // Endpoints HLS/DASH sin extensión, p. ej. `https://player.zilla-networks.com/m3u8/<hash>`.
    private val mediaPathRe = Regex(
        """/(?:m3u8|hls|dash|manifest)(?:/|\.|$)""",
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
        // Atributos con la URL en base64 (temas WordPress tipo animeflv.or.at).
        base64AttrRe.findAll(text).forEach { m ->
            decodeBase64(m.groupValues[1])?.let { raw += it }
        }

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
        if (mediaRe.containsMatchIn(url) || mediaPathRe.containsMatchIn(url)) return true
        if (Sites.isYtDlpHost(url)) return true
        return embedPathRe.containsMatchIn(url)
    }

    /**
     * Menor = mejor. Ojo: yt-dlp ya no trae extractores para la mayoría de estos reproductores,
     * así que casi todo lo resuelve el extractor GENÉRICO leyendo la página del embed.
     *  0 = archivo directo con extensión (.mp4/.m3u8/…): lo más fiable.
     *  1 = página de reproductor (embed): el genérico suele sacar el video de ahí.
     *  2 = endpoint HLS/DASH sin extensión (`…/m3u8/<hash>`): suele pedir tokens/cookies y falla.
     */
    private fun rank(url: String): Int = when {
        mediaRe.containsMatchIn(url) -> 0
        mediaPathRe.containsMatchIn(url) -> 2
        else -> 1
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

    /**
     * Decodifica el base64 de un atributo `data-*`. Prueba varios "sabores" (estándar, sin relleno,
     * URL-safe) porque cada tema usa el que quiere. Devuelve null si no es base64 de una URL.
     */
    private fun decodeBase64(raw: String): String? {
        val s = raw.trim()
        if (s.length < 12 || !s.matches(Regex("""[A-Za-z0-9+/_=-]+"""))) return null
        val attempts = intArrayOf(
            android.util.Base64.DEFAULT,
            android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
            android.util.Base64.URL_SAFE,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
        )
        for (flags in attempts) {
            val out = runCatching {
                String(android.util.Base64.decode(s, flags), Charsets.UTF_8).trim()
            }.getOrNull() ?: continue
            if (out.startsWith("http://") || out.startsWith("https://") || out.startsWith("//")) return out
        }
        return null
    }

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
