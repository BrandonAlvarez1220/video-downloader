package com.brandon.videodownloader.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Envoltorio sobre youtubedl-android.
 *
 * Cómo funciona por dentro (el "por qué" de que no haga falta backend):
 * el APK incluye un intérprete de Python y ffmpeg compilados para ARM. Al arrancar
 * se descomprimen en el almacenamiento privado de la app, y cada descarga lanza
 * `python yt-dlp <args>` como un PROCESO dentro del teléfono, igual que harías en una terminal.
 * Leemos su salida (stdout) para saber el progreso.
 */
class Engine(private val context: Context, private val scope: CoroutineScope) {

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
     */
    suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        awaitReady()
        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--flat-playlist")
            .addOption("--no-warnings")
        val response = YoutubeDL.execute(request, null, null)
        ProbeResult.parse(JSONObject(response.out), url)
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
