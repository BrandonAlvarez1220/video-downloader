package com.brandon.videodownloader.engine

import android.content.Context
import com.brandon.videodownloader.data.Settings
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Envoltorio sobre youtubedl-android.
 *
 * Cómo funciona por dentro (el "por qué" de que no haga falta backend):
 * el APK incluye un intérprete de Python y ffmpeg compilados para ARM. Al arrancar
 * se descomprimen en el almacenamiento privado de la app, y cada descarga lanza
 * `python yt-dlp <args>` como un PROCESO dentro del teléfono, igual que harías en una terminal.
 * Leemos su salida (stdout) para saber el progreso.
 */
class Engine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: Settings,
) {

    sealed interface State {
        data object Initializing : State
        data class Ready(val version: String?) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Initializing)
    val state: StateFlow<State> = _state

    fun initAsync() {
        scope.launch(Dispatchers.IO) {
            _state.value = try {
                // La primera vez tarda unos segundos: descomprime Python (~30 MB).
                YoutubeDL.init(context)
                FFmpeg.init(context)
                State.Ready(YoutubeDL.versionName(context))
            } catch (e: Exception) {
                State.Failed(e.message ?: e.toString())
            }
            if (_state.value is State.Ready) autoUpdateIfStale()
        }
    }

    /** Suspende hasta que el motor esté listo. Lanza excepción si falló la inicialización. */
    suspend fun awaitReady() {
        val s = state.first { it !is State.Initializing }
        if (s is State.Failed) throw IllegalStateException("Motor no disponible: ${s.message}")
    }

    /**
     * Los sitios cambian su código seguido y yt-dlp saca versiones para adaptarse.
     * Si una descarga empieza a fallar "de la nada", casi siempre la solución es actualizar.
     */
    suspend fun update(): String = withContext(Dispatchers.IO) {
        awaitReady()
        val status = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        val version = YoutubeDL.versionName(context)
        _state.value = State.Ready(version)
        if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) "Ya tienes la última versión ($version)"
        else "Actualizado a $version"
    }

    private suspend fun autoUpdateIfStale() {
        val last = prefs.getLong(KEY_LAST_UPDATE, 0)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return
        runCatching { update() } // sin internet o con límite de GitHub: no pasa nada, se reintenta mañana
    }

    private val prefs by lazy { context.getSharedPreferences("engine", Context.MODE_PRIVATE) }

    /**
     * Analiza una URL SIN descargar. `--flat-playlist` hace que, si es una lista,
     * solo traiga los enlaces de cada video (rápido) en vez de analizarlos todos.
     *
     * Si el enlace es de un agregador (animeflv, pelisplus…), primero se resuelve el iframe
     * del reproductor (ver [SiteResolver]). Si yt-dlp NO reconoce la página ("Unsupported URL"),
     * se intenta el plan B general: leer el HTML y sacar el iframe, sea el dominio que sea.
     */
    suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        awaitReady()
        val enabled = settings.fallbackResolve.value

        // Dominios del catálogo: resolvemos ANTES. Si no, primer intento directo con yt-dlp.
        val known = if (enabled && SiteResolver.matches(url)) {
            runCatching { SiteResolver.resolve(url) }.getOrNull()
        } else null

        var lastError: Exception? = null
        for (candidate in known?.mediaUrls ?: listOf(url)) {
            try {
                return@withContext runProbe(url, known, candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e // este candidato no sirvió: probamos el siguiente
            }
        }

        // Plan B general: yt-dlp no reconoció la página ("Unsupported URL"). Leemos el HTML,
        // sacamos el reproductor y probamos sus candidatos. Sirve para CUALQUIER dominio.
        if (known == null && enabled) {
            val fallback = runCatching { SiteResolver.tryResolve(url) }.getOrNull()
            if (fallback != null) {
                for (candidate in fallback.mediaUrls) {
                    try {
                        return@withContext runProbe(url, fallback, candidate)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
            }
        }
        throw lastError ?: IllegalStateException("No se pudo analizar el enlace")
    }

    /** Analiza un candidato y reetiqueta el resultado con la página original y el nombre del sitio. */
    private suspend fun runProbe(pageUrl: String, resolved: SiteResolver.Resolved?, target: String): ProbeResult {
        val request = YoutubeDLRequest(target)
            .addOption("--dump-single-json")
            .addOption("--flat-playlist")
            .addOption("--no-playlist") // enlace de video dentro de una lista = solo ese video
            .addOption("--no-warnings")
            .applyResolvedHeaders(resolved?.headers)
        val tmp = File(context.cacheDir, "probe/${System.nanoTime()}")
        Accounts.cookiesCopyFor(context, tmp)?.let { request.addOption("--cookies", it.absolutePath) }
        try {
            val response = YoutubeDL.execute(request, null, null)
            val parsed = ProbeResult.parse(JSONObject(response.out), target)
            // La PÁGINA manda para el título: el del reproductor suele ser un hash sin sentido.
            return if (resolved != null && parsed is ProbeResult.Video) {
                parsed.copy(url = pageUrl, site = resolved.siteName, title = resolved.title ?: parsed.title)
            } else {
                parsed
            }
        } finally {
            tmp.deleteRecursively()
        }
    }


    companion object {
        private const val KEY_LAST_UPDATE = "last_update"
    }
}

/** Calidades que ofrece la UI. "audio" = solo audio en MP3. */
object Quality {
    const val BEST = "best"
    const val AUDIO = "audio"
    val all = listOf(BEST, "2160", "1440", "1080", "720", "480", "360", AUDIO)

    fun label(q: String) = when (q) {
        BEST -> "Máxima"
        AUDIO -> "Solo audio (MP3)"
        "2160" -> "4K"
        "1440" -> "2K"
        else -> "${q}p"
    }

    /**
     * Traduce la calidad al lenguaje de selección de formatos de yt-dlp:
     * - `bv*` = mejor video (puede venir sin audio), `ba` = mejor audio.
     * - `bv*+ba` = baja ambos y ffmpeg los une. Así sirve YouTube todo lo mayor a 720p.
     * - `/b` = plan B: el mejor archivo que ya traiga video+audio juntos.
     * - `[height<=N]` = tope de resolución (si no existe, toma la inmediata inferior).
     */
    fun formatSelector(q: String): String = when (q) {
        AUDIO -> "ba/b"
        BEST -> "bv*+ba/b"
        else -> "bv*[height<=$q]+ba/b[height<=$q]/bv*+ba/b"
    }
}
