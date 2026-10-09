package com.brandon.videodownloader.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.brandon.videodownloader.App
import com.brandon.videodownloader.data.Status
import com.brandon.videodownloader.engine.Quality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Un Worker = una descarga. WorkManager se encarga de:
 *  - que siga corriendo con la app cerrada (servicio en primer plano + notificación),
 *  - reintentarla si Android mata el proceso o se va el internet (la guarda en su propia BD),
 *  - esperar a que haya red (Constraints).
 *
 * Es el equivalente a un BackgroundService/Hangfire en .NET, pero adaptado a las reglas
 * de batería de Android, que mata cualquier hilo "suelto" de una app en segundo plano.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app get() = applicationContext as App
    private val dao get() = app.database.downloads()

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        if (id < 0) return Result.failure()
        val job = dao.get(id) ?: return Result.success()
        if (!job.status.isActive) return Result.success() // cancelada o ya terminada

        val title = job.title ?: job.url
        return try {
            // Si la app está en segundo plano, Android 12+ puede negarlo; la descarga sigue igual.
            runCatching { setForeground(foregroundInfo(id, title, null, "En cola")) }
            app.engine.awaitReady()
            // Semáforo = máximo N descargas simultáneas; las demás esperan aquí su turno.
            slots.withPermit { download(id, title) }
            Result.success()
        } catch (e: CancellationException) {
            // WorkManager detuvo el trabajo. Si NO fue el usuario (p. ej. se fue la red),
            // lo dejamos en cola: WorkManager lo relanzará solo.
            withContext(NonCancellable) {
                dao.advanceStatus(id, Status.QUEUED)
            }
            throw e
        } catch (e: Exception) {
            if (dao.get(id)?.status != Status.CANCELLED) {
                dao.setStatus(id, Status.ERROR, cleanError(e), System.currentTimeMillis())
                Notifications.finished(applicationContext, id, title, ok = false)
            } else {
                workDir(id).deleteRecursively()
            }
            // success = "no reintentes tú": el usuario decide con el botón Reintentar.
            Result.success()
        }
    }

    private suspend fun download(id: Long, fallbackTitle: String) {
        val job = dao.get(id) ?: return
        if (!job.status.isActive) return
        dao.advanceStatus(id, Status.DOWNLOADING)

        val audioOnly = job.quality == Quality.AUDIO
        val dir = workDir(id).apply { mkdirs() }
        val request = YoutubeDLRequest(job.url).apply {
            addOption("-f", Quality.formatSelector(job.quality))
            // `.150B` recorta el título a 150 bytes: hay sistemas de archivos con límite de nombre.
            addOption("-o", File(dir, "%(title).150B [%(id)s].%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--write-info-json") // metadatos (título, resolución…) en un .json al lado
            addOption("--no-mtime")
            addOption("--retries", 5)
            addOption("--concurrent-fragments", 4) // acelera videos en trozos (HLS/DASH)
            if (audioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "192K")
            } else {
                addOption("--merge-output-format", "mp4") // MP4 = se reproduce en cualquier lado
            }
        }

        // El callback llega desde el hilo que lee la salida de yt-dlp, muchas veces por segundo.
        // Solo guardamos el último valor y otra corrutina lo escribe en la BD cada 500 ms
        // (escribir en cada línea saturaría SQLite y la UI sin aportar nada).
        val latest = AtomicReference<ProgressLine?>(null)
        coroutineScope {
            val reporter = launch {
                var processingMarked = false
                while (isActive) {
                    delay(500)
                    val p = latest.get() ?: continue
                    if (p.processing && !processingMarked) {
                        dao.advanceStatus(id, Status.PROCESSING)
                        processingMarked = true
                    }
                    dao.setProgress(id, p.percent, p.text)
                    runCatching { setForeground(foregroundInfo(id, fallbackTitle, p.percent, p.text)) }
                }
            }
            try {
                // runInterruptible: si cancelan la corrutina, interrumpe el hilo bloqueado
                // y la librería mata el proceso de Python.
                runInterruptible(Dispatchers.IO) {
                    YoutubeDL.execute(request, id.toString(), false) { percent, eta, line ->
                        latest.set(ProgressLine.parse(percent, eta, line, latest.get()))
                    }
                }
            } finally {
                reporter.cancel()
            }
        }

        // Tras el merge/conversión solo queda el archivo final (+ el .info.json).
        val media = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".json") && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }
            ?: error("yt-dlp terminó pero no se encontró el archivo descargado")
        val info = dir.listFiles()?.firstOrNull { it.name.endsWith(".info.json") }
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }

        dao.advanceStatus(id, Status.PROCESSING)
        dao.setProgress(id, 100f, "Guardando en la galería…")
        val saved = MediaStoreSaver.save(applicationContext, media)
        dir.deleteRecursively()

        val current = dao.get(id) ?: return
        if (current.status == Status.CANCELLED) return
        val done = current.copy(
            status = Status.COMPLETED,
            title = info?.str("title") ?: current.title ?: media.nameWithoutExtension,
            uploader = info?.str("uploader") ?: info?.str("channel") ?: current.uploader,
            site = info?.str("extractor_key") ?: current.site,
            thumbnail = info?.str("thumbnail") ?: current.thumbnail,
            durationSec = info?.optDouble("duration")?.takeIf { !it.isNaN() }?.toLong() ?: current.durationSec,
            width = info?.optInt("width")?.takeIf { it > 0 },
            height = info?.optInt("height")?.takeIf { it > 0 },
            progress = 100f,
            progressText = null,
            error = null,
            fileUri = saved.uri.toString(),
            fileName = saved.name,
            fileSize = saved.size,
            mimeType = saved.mime,
            finishedAt = System.currentTimeMillis(),
        )
        dao.update(done)
        Notifications.finished(applicationContext, id, done.title ?: fallbackTitle, ok = true)
    }

    private fun workDir(id: Long) = File(applicationContext.cacheDir, "downloads/$id")

    private fun foregroundInfo(id: Long, title: String, percent: Float?, text: String?): ForegroundInfo =
        ForegroundInfo(
            Notifications.progressId(id),
            Notifications.progress(applicationContext, title, percent, text),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    /** yt-dlp escribe todo su stderr; al usuario le sirve solo la(s) línea(s) con ERROR. */
    private fun cleanError(e: Exception): String {
        val msg = e.message ?: e.toString()
        val errors = msg.lines().filter { it.contains("ERROR") }.map { it.substringAfter("ERROR:").trim() }
        return (errors.lastOrNull() ?: msg.lines().lastOrNull { it.isNotBlank() } ?: msg).take(500)
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        const val KEY_ID = "download_id"
        const val MAX_CONCURRENT = 3
        private val slots = Semaphore(MAX_CONCURRENT)
    }
}

/** Interpreta una línea de salida de yt-dlp, p. ej.: `[download]  45.3% of ~100.00MiB at 2.50MiB/s ETA 00:30` */
internal data class ProgressLine(val percent: Float, val text: String?, val processing: Boolean) {
    companion object {
        private val sizeRe = Regex("""of\s+~?\s*([\d.]+\s?\w+)""")
        private val speedRe = Regex("""at\s+([\d.]+\s?\w+/s)""")
        // Etapas de post-proceso (ffmpeg): unir video+audio, convertir a mp3, reparar, etc.
        private val processingRe = Regex("""^\[(Merger|ExtractAudio|VideoConvertor|FixupM3u8|FixupM4a|FixupStretched|VideoRemuxer|ffmpeg)\]""")

        fun parse(percent: Float, eta: Long, line: String, previous: ProgressLine?): ProgressLine {
            if (processingRe.containsMatchIn(line)) return ProgressLine(100f, "Procesando con ffmpeg…", true)
            if (!line.startsWith("[download]")) return previous ?: ProgressLine(percent, null, false)
            val size = sizeRe.find(line)?.groupValues?.get(1)
            val speed = speedRe.find(line)?.groupValues?.get(1)
            val etaText = if (eta > 0) "%d:%02d".format(eta / 60, eta % 60) else null
            val text = listOfNotNull(size, speed, etaText).joinToString(" · ").ifBlank { null }
            return ProgressLine(percent.coerceIn(0f, 100f), text ?: previous?.text, false)
        }
    }
}
