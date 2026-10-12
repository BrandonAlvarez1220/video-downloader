package com.brandon.videodownloader.engine

import android.net.Uri

/**
 * Catálogo de sitios "agregadores" (anime y películas) que NO sirven el video directamente:
 * la página embebe un reproductor de OTRO dominio dentro de un `<iframe>`.
 *
 * Esto es justo lo que yt-dlp no resuelve solo: su extractor genérico lee el HTML, pero no
 * "entra" a los iframes de reproductores que no conoce. Por eso primero hay que averiguar la
 * URL del reproductor y recién entonces dársela a yt-dlp (ver [SiteResolver]).
 */
object Sites {

    enum class Kind { ANIME, PELIS }

    data class Aggregator(
        val id: String,
        val name: String,
        /** Coincidencias por "contiene" sobre el host (cubre espejos y TLDs variables). */
        val hosts: List<String>,
        val kind: Kind,
    )

    val all = listOf(
        Aggregator("animeflv", "AnimeFLV", listOf("animeflv.net", "animeflv.com"), Kind.ANIME),
        Aggregator("pelisplus", "PelisPlus", listOf("pelisplus"), Kind.PELIS),
        Aggregator("cuevana", "Cuevana", listOf("cuevana"), Kind.PELIS),
        Aggregator("repelis", "RePelis", listOf("repelis", "pelis24", "poseidonhd"), Kind.PELIS),
        Aggregator("gnula", "Gnula", listOf("gnula"), Kind.PELIS),
    )

    fun match(url: String): Aggregator? {
        val host = hostOf(url) ?: return null
        return all.firstOrNull { agg -> agg.hosts.any { host.contains(it) } }
    }

    /**
     * Reproductores que yt-dlp SÍ sabe resolver por sí mismo. Si el resolutor encuentra uno de
     * estos, se lo pasa tal cual (con Referer) y deja que yt-dlp haga el resto.
     */
    private val ytdlpHosts = listOf(
        "streamtape", "doodstream", "dood.", "filemoon", "byse", "mp4upload",
        "streamwish", "vidhide", "ahvsh", "embedwish", "voe.sx", "voe-",
        "mixdrop", "upstream", "vidoza", "vidmoly", "ok.ru", "okru", "goodstream",
        "uqload", "hgcloud", "luluvdo", "yourupload", "netu", "waaw", "fembed",
        "vidsrc", "sbplay", "sbembed", "fastplay", "streamsb", "vidcloud", "sendvid",
        "vudeo", "vembed", "streamhide", "hqq", "krakenfiles", "1cloudfile",
        "minochinos", "anavids", "vidsrc.to", "streamsb", "dood", "vidguard",
        "mega.nz", "1fichier", "filelions", "swish", "wish", "rpmstream",
    )

    fun isYtDlpHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return ytdlpHosts.any { host.contains(it) }
    }

    /** Anuncios, analítica y captchas: nunca son el video. */
    private val blockedHosts = listOf(
        "doubleclick", "googletagmanager", "google-analytics", "googlesyndication",
        "facebook.net", "fbcdn", "cloudflareinsights", "disqus", "histats",
        "amazon-adsystem", "adnxs", "criteo", "popads", "propeller", "onclck",
        "imasdk", "adservice", "analytics",
    )

    fun isBlocked(url: String): Boolean {
        val low = url.lowercase()
        return blockedHosts.any { low.contains(it) }
    }

    fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host }.getOrNull()?.lowercase()?.takeIf { it.isNotBlank() }
}
