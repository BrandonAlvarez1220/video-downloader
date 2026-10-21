package com.brandon.videodownloader.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.brandon.videodownloader.data.Download
import com.brandon.videodownloader.data.DownloadDao
import com.brandon.videodownloader.data.Settings
import com.brandon.videodownloader.data.Status
import com.brandon.videodownloader.engine.ProbeResult
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/** Punto único para agregar/cancelar/reintentar/borrar descargas (lo usa la UI). */
class DownloadController(
    private val context: Context,
    private val dao: DownloadDao,
    private val settings: Settings,
) {

    private val workManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(video: ProbeResult.Video, quality: String, vault: Boolean = false): Long {
        val id = dao.insert(
            Download(
                url = video.url,
                quality = quality,
                title = video.title,
                uploader = video.uploader,
                site = video.site,
                thumbnail = video.thumbnail,
                durationSec = video.durationSec,
                vault = vault,
            )
        )
        schedule(id)
        return id
    }

    /** Sin análisis previo (p. ej. si analizar falló): el worker mostrará el error real. */
    suspend fun enqueueUrl(url: String, quality: String, vault: Boolean = false): Long {
        val id = dao.insert(Download(url = url, quality = quality, vault = vault))
        schedule(id)
        return id
    }

    suspend fun cancel(id: Long) {
        dao.setStatus(id, Status.CANCELLED, finishedAt = System.currentTimeMillis())
        YoutubeDL.destroyProcessById(id.toString()) // mata el proceso de Python al instante
        workManager.cancelUniqueWork(workName(id))
        File(context.cacheDir, "downloads/$id").deleteRecursively()
    }

    suspend fun retry(id: Long) {
        val d = dao.get(id) ?: return
        dao.update(d.copy(status = Status.QUEUED, error = null, progress = 0f, progressText = null, finishedAt = null))
        schedule(id)
    }

    suspend fun removeFromQueue(id: Long) {
        val d = dao.get(id) ?: return
        if (d.status.isActive) cancel(id)
        File(context.cacheDir, "downloads/$id").deleteRecursively()
        dao.delete(id)
    }

    suspend fun clearFailures() = dao.clearFinishedFailures()

    /**
     * Pasa un video de la biblioteca a la Bóveda: lo copia al almacenamiento interno y borra la
     * copia pública. Devuelve false si la copia de la galería no se pudo borrar (pasa si la app se
     * reinstaló: Android ya no la reconoce como dueña de ese archivo).
     */
    suspend fun moveToVault(d: Download): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(d.fileUri ?: error("El video no tiene archivo"))
        if (uri.scheme == "file") {
            dao.update(d.copy(vault = true))
            return@withContext true
        }
        val saved = MediaStoreSaver.copyIntoVault(
            context, uri, d.fileName ?: "video.mp4", d.mimeType ?: "video/mp4",
        )
        dao.update(d.copy(vault = true, fileUri = saved.uri.toString(), fileName = saved.name, fileSize = saved.size))
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    /** Saca un video de la Bóveda: vuelve a la galería y a la biblioteca normal. */
    suspend fun moveOutOfVault(d: Download) = withContext(Dispatchers.IO) {
        val file = File(Uri.parse(d.fileUri ?: error("El video no tiene archivo")).path!!)
        val saved = MediaStoreSaver.save(context, file)
        dao.update(
            d.copy(vault = false, fileUri = saved.uri.toString(), fileName = saved.name, fileSize = saved.size, mimeType = saved.mime)
        )
        file.delete()
    }

    /**
     * Revisa que cada archivo siga existiendo y quita de la app lo que borraste por fuera
     * (por ejemplo, desde la galería). Aprovecha para generar miniaturas que falten.
     * Devuelve cuántos registros se quitaron.
     *
     * Por qué hace falta: la app guarda su propia lista (Room) y la galería tiene la suya
     * (MediaStore). Son dos "bases de datos" distintas; si borras en una, la otra no se entera sola.
     */
    suspend fun syncLibrary(): Int = withContext(Dispatchers.IO) {
        // Si ya hay una revisión en curso (p. ej. entraste y saliste rápido), no duplicamos trabajo.
        if (!syncLock.tryLock()) return@withContext 0
        try {
        var removed = 0
        for (d in dao.completed()) {
            val uri = d.fileUri?.let(Uri::parse)
            val exists = when {
                uri == null -> false
                uri.scheme == "file" -> File(uri.path ?: "").exists()
                // Si no podemos consultar (permiso, etc.) asumimos que existe: nunca borrar por duda.
                else -> runCatching {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                        ?.use { it.moveToFirst() } ?: true
                }.getOrDefault(true)
            }
            if (!exists) {
                dao.delete(d.id)
                Thumbnails.delete(context, d.id)
                removed++
            } else if (!Thumbnails.isLocalAndPresent(d.thumbnail)) {
                // Videos viejos o sin imagen: miniatura propia sacada del archivo.
                Thumbnails.fromUri(context, d.id, uri!!)?.let { dao.setThumbnail(d.id, it) }
            }
        }
        removed
        } finally {
            syncLock.unlock()
        }
    }

    private val syncLock = Mutex()

    /** Cambia el título en la app y, si está en la galería, también el nombre del archivo allá. */
    suspend fun rename(d: Download, newTitle: String) = withContext(Dispatchers.IO) {
        val title = newTitle.trim()
        if (title.isEmpty()) return@withContext
        dao.setTitle(d.id, title)
        val uri = d.fileUri?.let(Uri::parse) ?: return@withContext
        if (uri.scheme != "file") {
            val ext = d.fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            val safe = title.replace(Regex("""[\\/:*?"<>|]+"""), " ").trim().take(120)
            runCatching {
                context.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.TITLE, title)
                    put(MediaStore.MediaColumns.DISPLAY_NAME, if (ext != null) "$safe.$ext" else safe)
                }, null, null)
            } // si Android no lo permite (app reinstalada), el título igual cambia dentro de la app
        }
    }

    /** Borra el archivo de la galería y el registro. */
    suspend fun deleteFromLibrary(d: Download) {
        Thumbnails.delete(context, d.id)
        d.fileUri?.let { raw ->
            val uri = Uri.parse(raw)
            runCatching {
                if (uri.scheme == "file") File(uri.path!!).delete()
                else context.contentResolver.delete(uri, null, null)
            }
        }
        dao.delete(d.id)
    }

    private fun schedule(id: Long) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            // UNMETERED = solo redes que no cobran por datos (Wi-Fi).
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG)
            .build()
        // "Unique work" por id: evita que la misma descarga se encole dos veces.
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: Long) = "download-$id"

    companion object {
        const val TAG = "download"
    }
}
