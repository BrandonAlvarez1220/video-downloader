package com.brandon.videodownloader.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/**
 * Copia el archivo terminado a la galería pública (Movies/VideoDownloader o Music/VideoDownloader).
 *
 * Por qué no descargar ahí directamente: desde Android 10 existe el "almacenamiento acotado"
 * (scoped storage). Una app no puede escribir rutas públicas a su antojo, pero sí puede crear
 * archivos a través de MediaStore SIN pedir permisos. yt-dlp trabaja con rutas normales,
 * así que descarga en la carpeta privada de la app y al final copiamos con MediaStore.
 * Bonus: los archivos creados así son "de la app" y se pueden borrar sin pedir confirmación.
 */
object MediaStoreSaver {

    data class Saved(val uri: Uri, val name: String, val size: Long, val mime: String)

    fun save(context: Context, file: File): Saved {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val (collection, folder) = when {
            mime.startsWith("video/") -> MediaStore.Video.Media.getContentUri(volume) to Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(volume) to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.getContentUri(volume) to Environment.DIRECTORY_DOWNLOADS
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/VideoDownloader")
            // IS_PENDING = "aún lo estoy escribiendo": la galería no lo muestra a medias.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IOException("No se pudo crear el archivo en la galería")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out, bufferSize = 1 shl 20) }
            } ?: throw IOException("No se pudo abrir el archivo de destino")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved(uri, file.name, file.length(), mime)
    }

    /**
     * Modo privado: el archivo se queda en el almacenamiento INTERNO de la app.
     * Ni la galería ni otras apps (ni un explorador de archivos sin root) pueden verlo;
     * solo se ve y reproduce dentro de Video Downloader. Se borra si desinstalas la app.
     */
    fun savePrivate(context: Context, file: File): Saved {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val dir = privateDir(context).apply { mkdirs() }
        var dest = File(dir, file.name)
        var n = 1
        while (dest.exists()) dest = File(dir, "${file.nameWithoutExtension} (${n++}).${file.extension}")
        // Mismo almacenamiento interno que la carpeta temporal: renameTo es instantáneo (no copia).
        if (!file.renameTo(dest)) {
            file.copyTo(dest)
            file.delete()
        }
        return Saved(Uri.fromFile(dest), dest.name, dest.length(), mime)
    }

    fun privateDir(context: Context) = File(context.filesDir, "private")
}
