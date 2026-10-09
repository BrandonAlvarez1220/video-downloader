package com.brandon.videodownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una sola tabla sirve como COLA y como BIBLIOTECA: el [status] dice en qué etapa va.
 * La biblioteca es simplemente `WHERE status = 'COMPLETED'`.
 *
 * Es un `data class` (inmutable): para cambiar algo se hace `copy(...)`,
 * igual que un `record` con `with { }` en C#.
 */
@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val quality: String,
    val status: Status = Status.QUEUED,
    val title: String? = null,
    val uploader: String? = null,
    val site: String? = null,
    val thumbnail: String? = null,
    val durationSec: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** 0..100 del archivo que se está bajando en este momento. */
    val progress: Float = 0f,
    /** Texto corto para la UI: "45 MiB · 2.3 MiB/s · 0:30". */
    val progressText: String? = null,
    val error: String? = null,
    /** content:// URI del archivo guardado en la galería (MediaStore). */
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

enum class Status {
    QUEUED, DOWNLOADING, PROCESSING, COMPLETED, ERROR, CANCELLED;

    val isActive get() = this == QUEUED || this == DOWNLOADING || this == PROCESSING
}
