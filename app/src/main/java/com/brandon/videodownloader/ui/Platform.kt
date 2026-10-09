package com.brandon.videodownloader.ui

import android.net.Uri
import androidx.compose.ui.graphics.Color

/** Reconoce la plataforma de un enlace (o del "extractor" de yt-dlp) para mostrar su insignia. */
data class Platform(val name: String, val color: Color) {
    companion object {
        private val known = listOf(
            listOf("youtube", "youtu.be") to Platform("YouTube", Color(0xFFFF0033)),
            listOf("tiktok") to Platform("TikTok", Color(0xFF25F4EE)),
            listOf("instagram") to Platform("Instagram", Color(0xFFE1306C)),
            listOf("facebook", "fb.watch") to Platform("Facebook", Color(0xFF1877F2)),
            listOf("twitter", "x.com") to Platform("X", Color(0xFFB0B0B0)),
            listOf("twitch") to Platform("Twitch", Color(0xFF9146FF)),
            listOf("vimeo") to Platform("Vimeo", Color(0xFF1AB7EA)),
            listOf("reddit", "redd.it") to Platform("Reddit", Color(0xFFFF4500)),
            listOf("soundcloud") to Platform("SoundCloud", Color(0xFFFF7700)),
            listOf("dailymotion") to Platform("Dailymotion", Color(0xFF0A84FF)),
            listOf("pinterest", "pin.it") to Platform("Pinterest", Color(0xFFE60023)),
            listOf("bilibili") to Platform("Bilibili", Color(0xFF00A1D6)),
            listOf("kick.com") to Platform("Kick", Color(0xFF53FC18)),
            listOf("bandcamp") to Platform("Bandcamp", Color(0xFF1DA0C3)),
            listOf("threads") to Platform("Threads", Color(0xFFB0B0B0)),
            listOf("linkedin") to Platform("LinkedIn", Color(0xFF0A66C2)),
        )
        private val web = Platform("Web", Color(0xFF8E8EA8))

        fun fromUrl(url: String): Platform {
            val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return web
            return known.firstOrNull { (keys, _) -> keys.any { host.contains(it) } }?.second ?: web
        }

        /** `site` es el extractor_key de yt-dlp ("Youtube", "TikTok", "Generic"…). */
        fun fromSite(site: String?, url: String): Platform {
            val s = site?.lowercase() ?: return fromUrl(url)
            return known.firstOrNull { (keys, _) -> keys.any { s.contains(it.substringBefore('.')) } }?.second
                ?: if (s == "generic") fromUrl(url) else Platform(site, web.color)
        }
    }
}
