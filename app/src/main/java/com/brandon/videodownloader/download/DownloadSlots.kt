package com.brandon.videodownloader.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Limita cuántas descargas corren a la vez. Es como un semáforo, pero con un límite
 * que puede cambiar en caliente desde Ajustes (un Semaphore normal tiene permisos fijos).
 */
object DownloadSlots {
    private val mutex = Mutex()
    private val running = MutableStateFlow(0)

    suspend fun <T> withSlot(limit: () -> Int, block: suspend () -> T): T {
        while (true) {
            val acquired = mutex.withLock {
                if (running.value < limit()) { running.update { it + 1 }; true } else false
            }
            if (acquired) break
            // Espera a que alguien libere un lugar (o reintenta cada 2 s por si subió el límite).
            withTimeoutOrNull(2_000) { running.first { it < limit() } }
        }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }
}
