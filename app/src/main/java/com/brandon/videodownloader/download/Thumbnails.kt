package com.brandon.videodownloader.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/**
 * Miniaturas propias, sacadas de un cuadro del video ya descargado.
 *
 * Por qué no depender solo de la imagen del sitio: muchos sitios no dan miniatura, la URL caduca
 * o el servidor la borra, y sin internet no carga. Un cuadro del propio archivo siempre existe,
 * funciona sin conexión y, en la Bóveda, no deja rastro en ningún servidor externo.
 */
object Thumbnails {

    private fun fileFor(context: Context, id: Long) = File(File(context.filesDir, "thumbs"), "$id.jpg")

    fun fromFile(context: Context, id: Long, media: File): String? =
        extract(context, id) { it.setDataSource(media.absolutePath) }

    fun fromUri(context: Context, id: Long, uri: Uri): String? =
        if (uri.scheme == "file") extract(context, id) { it.setDataSource(uri.path) }
        else extract(context, id) { it.setDataSource(context, uri) }

    /** true si la miniatura es local y el archivo sigue existiendo. */
    fun isLocalAndPresent(thumbnail: String?): Boolean {
        val uri = thumbnail?.let(Uri::parse) ?: return false
        return uri.scheme == "file" && File(uri.path ?: return false).exists()
    }

    fun delete(context: Context, id: Long) {
        fileFor(context, id).delete()
    }

    private fun extract(context: Context, id: Long, open: (MediaMetadataRetriever) -> Unit): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            open(retriever)
            // MP3: la carátula incrustada. Video: un cuadro al 10 % (máx. 15 s) para saltarse
            // pantallas negras o logos del inicio.
            val bitmap: Bitmap = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?: run {
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val atUs = (durationMs * 1000 / 10).coerceAtMost(15_000_000L)
                    retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
                        ?: retriever.frameAtTime
                }
                ?: return@runCatching null
            val out = fileFor(context, id).apply { parentFile?.mkdirs() }
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            Uri.fromFile(out).toString()
        } finally {
            retriever.release()
        }
    }.getOrNull()
}
