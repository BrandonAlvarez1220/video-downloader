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
import kotlinx.coroutines.channels.Channel
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

    sealed interface Analysis {
        data object Idle : Analysis
        data object Loading : Analysis
        data class Done(val url: String, val result: ProbeResult) : Analysis
        data class Failed(val message: String) : Analysis
    }

    // ---- estado observable por la UI (Compose se redibuja cuando cambian) ----
    var tab by mutableIntStateOf(0)
    var input by mutableStateOf("")
        private set
    var quality by mutableStateOf("1080")
    var analysis by mutableStateOf<Analysis>(Analysis.Idle)
        private set
    var busyMessage by mutableStateOf<String?>(null)
        private set
    var search by mutableStateOf("")

    val engineState: StateFlow<Engine.State> = app.engine.state
    val queue: StateFlow<List<Download>> = app.database.downloads().observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val library: StateFlow<List<Download>> = app.database.downloads().observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mensajes de una sola vez (snackbar). Channel = se consumen una vez, no se repiten al rotar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val urls: List<String> get() = extractUrls(input)

    fun onInputChange(text: String) {
        input = text
        val a = analysis
        if (a is Analysis.Done && urls.singleOrNull() != a.url) analysis = Analysis.Idle
        if (a is Analysis.Failed) analysis = Analysis.Idle
    }

    /** Llamado cuando otra app "comparte" un enlace hacia esta. */
    fun onSharedText(text: String) {
        tab = 0
        onInputChange(text)
        if (urls.size == 1) analyze()
    }

    fun analyze() {
        val url = urls.singleOrNull() ?: return
        analysis = Analysis.Loading
        viewModelScope.launch {
            analysis = try {
                Analysis.Done(url, app.engine.probe(url))
            } catch (e: Exception) {
                Analysis.Failed(shortError(e))
            }
        }
    }

    fun downloadAll() {
        val list = urls
        if (list.isEmpty() || busyMessage != null) return
        val q = quality
        viewModelScope.launch {
            var added = 0
            list.forEachIndexed { i, url ->
                busyMessage = if (list.size > 1) "Analizando ${i + 1} de ${list.size}…" else "Analizando…"
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
            _messages.send(if (added == 1) "1 descarga agregada" else "$added descargas agregadas")
            tab = 1
        }
    }

    fun cancel(id: Long) = viewModelScope.launch { controller.cancel(id) }
    fun retry(id: Long) = viewModelScope.launch { controller.retry(id) }
    fun remove(id: Long) = viewModelScope.launch { controller.removeFromQueue(id) }
    fun clearFailures() = viewModelScope.launch { controller.clearFailures() }
    fun deleteFromLibrary(d: Download) = viewModelScope.launch {
        controller.deleteFromLibrary(d)
        _messages.send("Eliminado")
    }

    fun updateEngine() = viewModelScope.launch {
        _messages.send("Buscando actualización de yt-dlp…")
        val msg = runCatching { app.engine.update() }.getOrElse { "No se pudo actualizar: ${shortError(it)}" }
        _messages.send(msg)
    }

    fun toast(message: String) = viewModelScope.launch { _messages.send(message) }

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
