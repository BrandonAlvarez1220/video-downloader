package com.brandon.videodownloader.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.brandon.videodownloader.data.Download
import com.brandon.videodownloader.data.DownloadDao
import com.brandon.videodownloader.data.Status
import com.brandon.videodownloader.engine.ProbeResult
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/** Punto único para agregar/cancelar/reintentar/borrar descargas (lo usa la UI). */
class DownloadController(private val context: Context, private val dao: DownloadDao) {

    private val workManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(video: ProbeResult.Video, quality: String): Long {
        val id = dao.insert(
            Download(
                url = video.url,
                quality = quality,
                title = video.title,
                uploader = video.uploader,
                site = video.site,
                thumbnail = video.thumbnail,
                durationSec = video.durationSec,
            )
        )
        schedule(id)
        return id
    }

    /** Sin análisis previo (p. ej. si analizar falló): el worker mostrará el error real. */
    suspend fun enqueueUrl(url: String, quality: String): Long {
        val id = dao.insert(Download(url = url, quality = quality))
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

    /** Borra el archivo de la galería y el registro. */
    suspend fun deleteFromLibrary(d: Download) {
        d.fileUri?.let { runCatching { context.contentResolver.delete(Uri.parse(it), null, null) } }
        dao.delete(d.id)
    }

    private fun schedule(id: Long) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
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
