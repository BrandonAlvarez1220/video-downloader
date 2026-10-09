package com.brandon.videodownloader.engine

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Sesiones de usuario para contenido que exige login: videos con restricción de edad,
 * privados, de cuentas que sigues, o sitios premium.
 *
 * Cómo funciona: inicias sesión en un WebView (un navegador dentro de la app). El WebView guarda
 * las cookies de sesión; aquí las exportamos al formato "cookies.txt" de Netscape, que es el que
 * entiende yt-dlp (`--cookies archivo`). Así yt-dlp hace las peticiones "como si fueras tú".
 * Las cookies nunca salen del teléfono.
 */
object Accounts {

    data class Site(
        val id: String,
        val name: String,
        val loginUrl: String,
        /** URLs de las que se exportan cookies (un login de YouTube vive en youtube.com y google.com). */
        val cookieUrls: List<String>,
        /** Cookie que solo existe con la sesión iniciada: sirve para saber si estás logueado. */
        val sessionCookie: String,
        val color: Color,
    )

    val sites = listOf(
        Site(
            "youtube", "YouTube",
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F",
            listOf("https://www.youtube.com", "https://accounts.google.com", "https://www.google.com"),
            "SID", Color(0xFFFF0033),
        ),
        Site("instagram", "Instagram", "https://www.instagram.com/accounts/login/",
            listOf("https://www.instagram.com"), "sessionid", Color(0xFFE1306C)),
        Site("facebook", "Facebook", "https://m.facebook.com/login/",
            listOf("https://www.facebook.com", "https://m.facebook.com"), "c_user", Color(0xFF1877F2)),
        Site("x", "X / Twitter", "https://x.com/i/flow/login",
            listOf("https://x.com", "https://twitter.com"), "auth_token", Color(0xFFB0B0B0)),
        Site("tiktok", "TikTok", "https://www.tiktok.com/login",
            listOf("https://www.tiktok.com"), "sessionid", Color(0xFF25F4EE)),
        Site("reddit", "Reddit", "https://www.reddit.com/login/",
            listOf("https://www.reddit.com"), "reddit_session", Color(0xFFFF4500)),
    )

    private const val PREFS = "accounts"
    private const val KEY_CUSTOM = "custom_hosts"

    fun cookiesFile(context: Context) = File(context.filesDir, "cookies.txt")

    /** Sitios "otros" en los que iniciaste sesión (p. ej. un sitio premium que no está en la lista). */
    fun customHosts(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_CUSTOM, emptySet()).orEmpty()

    fun addCustomHost(context: Context, url: String) {
        val host = Uri.parse(url).host ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_CUSTOM, customHosts(context) + host).apply()
    }

    fun isLoggedIn(site: Site): Boolean = runCatching {
        val cm = CookieManager.getInstance()
        site.cookieUrls.any { url ->
            cm.getCookie(url)?.split(';')?.any { it.trim().startsWith("${site.sessionCookie}=") } == true
        }
    }.getOrDefault(false)

    /**
     * Escribe cookies.txt con las cookies de todos los sitios conocidos + los personalizados.
     * Formato Netscape: dominio, subdominios, ruta, solo-https, expiración, nombre, valor (separados por TAB).
     */
    fun export(context: Context): Int {
        val cm = CookieManager.getInstance()
        cm.flush()
        val urls = sites.flatMap { it.cookieUrls } + customHosts(context).map { "https://$it" }
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val seen = HashSet<String>()
        val lines = mutableListOf("# Netscape HTTP Cookie File")
        for (url in urls) {
            val raw = runCatching { cm.getCookie(url) }.getOrNull() ?: continue
            val domain = "." + baseDomain(Uri.parse(url).host ?: continue)
            raw.split(';').forEach { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isNotEmpty() && seen.add("$domain|$name")) {
                    lines += listOf(domain, "TRUE", "/", "TRUE", expiry.toString(), name, value).joinToString("\t")
                }
            }
        }
        val file = cookiesFile(context)
        if (lines.size == 1) file.delete() else file.writeText(lines.joinToString("\n") + "\n")
        return lines.size - 1
    }

    fun logoutAll(context: Context) {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        cookiesFile(context).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * Copia de cookies.txt para UN proceso de yt-dlp. Por qué una copia: yt-dlp reescribe el archivo
     * al terminar; con varias descargas en paralelo escribiendo el mismo archivo, se corrompería.
     */
    fun cookiesCopyFor(context: Context, dir: File): File? {
        val src = cookiesFile(context)
        if (!src.exists() || src.length() == 0L) return null
        dir.mkdirs()
        return File(dir, "cookies.txt").also { src.copyTo(it, overwrite = true) }
    }

    /** "www.m.youtube.com" -> "youtube.com" (suficiente para los dominios .com/.net habituales). */
    private fun baseDomain(host: String): String {
        val parts = host.split('.')
        return if (parts.size <= 2) host else parts.takeLast(2).joinToString(".")
    }
}
