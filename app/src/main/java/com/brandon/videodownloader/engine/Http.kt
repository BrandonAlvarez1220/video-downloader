package com.brandon.videodownloader.engine

import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Cliente HTTP mínimo para "leer" la página de un sitio de streaming antes de dársela a yt-dlp.
 *
 * ¿Por qué no OkHttp? Para no sumar dependencias: aquí solo hacemos GET de HTML y seguimos
 * redirecciones. Lo importante es que mandamos cabeceras de navegador real (UA, Accept, Referer)
 * y reutilizamos las cookies del WebView de "Cuentas", porque varios de estos sitios bloquean
 * las peticiones que "huelen" a bot.
 */
internal object Http {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    data class Response(val body: String, val finalUrl: String, val code: Int)

    fun get(
        url: String,
        referer: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            )
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Upgrade-Insecure-Requests", "1")
            referer?.let { setRequestProperty("Referer", it) }
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            // Sesión del WebView (Cuentas): si el sitio pide login, ya la tenemos.
            runCatching { CookieManager.getInstance().getCookie(url) }
                .getOrNull()?.takeIf { it.isNotBlank() }
                ?.let { setRequestProperty("Cookie", it) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.use { it.readBytes() } ?: ByteArray(0)
            val body = if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                runCatching {
                    GZIPInputStream(raw.inputStream()).bufferedReader().use { it.readText() }
                }.getOrElse { String(raw, Charsets.UTF_8) }
            } else {
                String(raw, Charsets.UTF_8)
            }
            return Response(body, conn.url?.toString() ?: url, code)
        } finally {
            conn.disconnect()
        }
    }
}
