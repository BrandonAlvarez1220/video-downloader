package com.brandon.videodownloader.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brandon.videodownloader.App
import com.brandon.videodownloader.data.Download
import com.brandon.videodownloader.engine.Engine
import com.brandon.videodownloader.engine.ProbeResult
import com.brandon.videodownloader.engine.Quality
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel = el estado de la pantalla + las acciones, separado de cómo se dibuja.
 * Sobrevive a rotaciones de pantalla (la Activity se destruye y recrea, el ViewModel no).
 * Es el patrón MVVM que quizá conoces de WPF/Xamarin.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val controller = app.downloads
    val settings = app.settings

    sealed interface Analysis {
        data object Idle : Analysis
        data object Loading : Analysis
        data class Done(val url: String, val result: ProbeResult) : Analysis
        data class Failed(val message: String) : Analysis
    }

    /** Pantallas que se abren encima de las pestañas. */
    sealed interface Overlay {
        data object Settings : Overlay
        data class Player(val download: Download) : Overlay
        data object Accounts : Overlay
    }

    enum class LibraryFilter { ALL, VIDEO, AUDIO, PRIVATE }
    enum class LibrarySort(val label: String) { RECENT("Recientes"), NAME("Nombre"), SIZE("Tamaño") }

    // ---- navegación ----
    var tab by mutableIntStateOf(0)
    var overlay by mutableStateOf<Overlay?>(null)

    // ---- pestaña Descargar ----
    var input by mutableStateOf("")
        private set
    var quality by mutableStateOf(settings.defaultQuality.value)
        private set
    private var lastVideoQuality = quality.takeIf { it != Quality.AUDIO } ?: "1080"
    var analysis by mutableStateOf<Analysis>(Analysis.Idle)
        private set
    var busyMessage by mutableStateOf<String?>(null)
        private set
    /** Hoja inferior de descarga rápida (cuando llega un enlace desde "Compartir"). */
    var quickSheet by mutableStateOf(false)

    // ---- pestaña Biblioteca ----
    var search by mutableStateOf("")
    var libraryFilter by mutableStateOf(LibraryFilter.ALL)
    var librarySite by mutableStateOf<String?>(null)
    var librarySort by mutableStateOf(LibrarySort.RECENT)
    var libraryGrid by mutableStateOf(true)

    var updatingEngine by mutableStateOf(false)
        private set

    val engineState: StateFlow<Engine.State> = app.engine.state
    val queue: StateFlow<List<Download>> = app.database.downloads().observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val library: StateFlow<List<Download>> = app.database.downloads().observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mensajes de una sola vez (snackbar). Channel = se consumen una vez, no se repiten al rotar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val urls: List<String> get() = extractUrls(input)
    val isAudio: Boolean get() = quality == Quality.AUDIO

    private var analyzeJob: Job? = null

    fun onInputChange(text: String) {
        input = text
        val single = urls.singleOrNull()
        val current = analysis
        if (single == null) {
            analyzeJob?.cancel()
            analysis = Analysis.Idle
            return
        }
        val alreadyFor = (current as? Analysis.Done)?.url
        if (alreadyFor == single || analyzingUrl == single) return
        // "Debounce": espera a que dejes de escribir antes de analizar.
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            delay(500)
            analyzeNow(single)
        }
    }

    fun selectQuality(q: String) {
        quality = q
        if (q != Quality.AUDIO) lastVideoQuality = q
    }

    fun setAudioMode(audio: Boolean) = selectQuality(if (audio) Quality.AUDIO else lastVideoQuality)

    /** Llamado cuando otra app "comparte" un enlace hacia esta: abre la hoja rápida. */
    fun onSharedText(text: String) {
        tab = 0
        overlay = null
        input = text
        val url = urls.firstOrNull() ?: return
        quickSheet = true
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch { analyzeNow(url) }
    }

    fun retryAnalysis() {
        val url = urls.singleOrNull() ?: return
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch { analyzeNow(url) }
    }

    private var analyzingUrl: String? = null

    private suspend fun analyzeNow(url: String) {
        analyzingUrl = url
        analysis = Analysis.Loading
        try {
            analysis = Analysis.Done(url, app.engine.probe(url))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            analysis = Analysis.Failed(shortError(e))
        } finally {
            if (analyzingUrl == url) analyzingUrl = null
        }
    }

    fun downloadAll() {
        val list = urls
        if (list.isEmpty() || busyMessage != null) return
        val q = quality
        viewModelScope.launch {
            var added = 0
            list.forEachIndexed { i, url ->
                busyMessage = if (list.size > 1) "Analizando ${i + 1} de ${list.size}…" else "Preparando…"
                // Reutiliza el análisis si ya se hizo para esta URL.
                val known = (analysis as? Analysis.Done)?.takeIf { it.url == url }?.result
                val result = known ?: runCatching { app.engine.probe(url) }.getOrNull()
                when (result) {
                    is ProbeResult.Video -> { controller.enqueue(result, q); added++ }
                    is ProbeResult.Playlist -> result.entries.forEach { controller.enqueue(it, q); added++ }
                    // Si el análisis falló, igual se encola: el worker mostrará el error real en la Cola.
                    null -> { controller.enqueueUrl(url, q); added++ }
                }
            }
            busyMessage = null
            input = ""
            analysis = Analysis.Idle
            quickSheet = false
            _messages.send(if (added == 1) "Descarga agregada a la cola" else "$added descargas agregadas a la cola")
            tab = 1
        }
    }

    fun dismissQuickSheet() {
        quickSheet = false
    }

    fun cancel(id: Long) = viewModelScope.launch { controller.cancel(id) }
    fun retry(id: Long) = viewModelScope.launch { controller.retry(id) }
    fun remove(id: Long) = viewModelScope.launch { controller.removeFromQueue(id) }
    fun clearFailures() = viewModelScope.launch { controller.clearFailures() }
    fun deleteFromLibrary(d: Download) = viewModelScope.launch {
        controller.deleteFromLibrary(d)
        _messages.send("Eliminado")
    }

    fun updateEngine() {
        if (updatingEngine) return
        updatingEngine = true
        viewModelScope.launch {
            val msg = runCatching { app.engine.update() }.getOrElse { "No se pudo actualizar: ${shortError(it)}" }
            updatingEngine = false
            _messages.send(msg)
        }
    }

    fun toast(message: String) = viewModelScope.launch { _messages.send(message) }

    /** Filtro + búsqueda + orden de la biblioteca (en memoria: son pocos cientos de filas). */
    fun filterLibrary(all: List<Download>): List<Download> {
        val q = search.trim()
        return all.asSequence()
            .filter {
                when (libraryFilter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.AUDIO -> it.mimeType?.startsWith("audio/") == true
                    LibraryFilter.VIDEO -> it.mimeType?.startsWith("audio/") != true
                    LibraryFilter.PRIVATE -> it.fileUri?.startsWith("file:") == true
                }
            }
            .filter { librarySite == null || it.site == librarySite }
            .filter { q.isEmpty() || listOfNotNull(it.title, it.uploader, it.site).any { s -> s.contains(q, true) } }
            .let { seq ->
                when (librarySort) {
                    LibrarySort.RECENT -> seq.sortedByDescending { it.finishedAt ?: it.createdAt }
                    LibrarySort.NAME -> seq.sortedBy { (it.title ?: it.fileName ?: "").lowercase() }
                    LibrarySort.SIZE -> seq.sortedByDescending { it.fileSize ?: 0 }
                }
            }
            .toList()
    }

    companion object {
        private val urlRegex = Regex("""https?://[^\s<>"']+""")

        /** Acepta texto "sucio" (p. ej. "Mira este video https://… vía TikTok") y saca los enlaces. */
        fun extractUrls(text: String): List<String> =
            urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ']') }.distinct().toList()

        fun shortError(e: Throwable): String {
            val msg = e.message ?: e.toString()
            return (msg.lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()
                ?: msg.lines().firstOrNull { it.isNotBlank() } ?: msg).take(300)
        }
    }
}
