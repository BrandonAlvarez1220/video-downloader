package com.brandon.videodownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO = interfaz con las consultas. Room genera la implementación al compilar (KSP).
 *
 * Las funciones que devuelven [Flow] son "consultas vivas": cada vez que la tabla cambia,
 * emiten la lista nueva y la UI se redibuja sola. Por eso no hace falta hacer polling.
 */
@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(download: Download): Long

    @Update
    suspend fun update(download: Download)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): Download?

    @Query("SELECT * FROM downloads WHERE status != 'COMPLETED' ORDER BY id DESC")
    fun observeQueue(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY finishedAt DESC")
    fun observeLibrary(): Flow<List<Download>>

    @Query("UPDATE downloads SET status = :status, error = :error, finishedAt = :finishedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: Status, error: String? = null, finishedAt: Long? = null)

    /**
     * Cambia de etapa SOLO si la descarga sigue activa. Al ser un único UPDATE es atómico:
     * así un worker nunca "revive" una descarga que el usuario canceló en ese mismo instante.
     */
    @Query("UPDATE downloads SET status = :status WHERE id = :id AND status IN ('QUEUED', 'DOWNLOADING', 'PROCESSING')")
    suspend fun advanceStatus(id: Long, status: Status)

    @Query("UPDATE downloads SET progress = :progress, progressText = :text WHERE id = :id")
    suspend fun setProgress(id: Long, progress: Float, text: String?)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('ERROR', 'CANCELLED')")
    suspend fun clearFinishedFailures()
}
