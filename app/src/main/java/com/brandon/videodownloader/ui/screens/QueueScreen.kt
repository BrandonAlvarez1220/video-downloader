package com.brandon.videodownloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.brandon.videodownloader.data.Download
import com.brandon.videodownloader.data.Status
import com.brandon.videodownloader.engine.Quality
import com.brandon.videodownloader.ui.MainViewModel

@Composable
fun QueueScreen(vm: MainViewModel, queue: List<Download>) {
    if (queue.isEmpty()) {
        EmptyState(Icons.Filled.Downloading, "No hay descargas en curso", "Pega un enlace en la pestaña Descargar.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (queue.any { it.status == Status.ERROR || it.status == Status.CANCELLED }) {
            item {
                TextButton(onClick = { vm.clearFailures() }) { Text("Limpiar fallidas y canceladas") }
            }
        }
        // `key` le dice a Compose qué fila es cuál: solo redibuja la que cambió de progreso.
        items(queue, key = { it.id }) { d -> QueueItem(d, vm) }
    }
}

@Composable
private fun QueueItem(d: Download, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = d.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    d.title ?: d.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${statusLabel(d.status)} · ${Quality.label(d.quality)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d.status == Status.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (d.status) {
                    Status.DOWNLOADING -> LinearProgressIndicator(
                        progress = { d.progress / 100f }, modifier = Modifier.fillMaxWidth(),
                    )
                    Status.PROCESSING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Unit
                }
                if (d.status.isActive && d.progressText != null) {
                    Text(
                        (if (d.status == Status.DOWNLOADING) "${d.progress.toInt()}% · " else "") + d.progressText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (d.status == Status.ERROR && d.error != null) {
                    Text(
                        d.error, style = MaterialTheme.typography.labelSmall, maxLines = 4,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column {
                if (d.status.isActive) {
                    IconButton(onClick = { vm.cancel(d.id) }) { Icon(Icons.Filled.Close, "Cancelar") }
                } else {
                    IconButton(onClick = { vm.retry(d.id) }) { Icon(Icons.Filled.Refresh, "Reintentar") }
                    IconButton(onClick = { vm.remove(d.id) }) { Icon(Icons.Filled.Delete, "Quitar") }
                }
            }
        }
    }
}

private fun statusLabel(s: Status) = when (s) {
    Status.QUEUED -> "En cola"
    Status.DOWNLOADING -> "Descargando"
    Status.PROCESSING -> "Procesando"
    Status.COMPLETED -> "Completado"
    Status.ERROR -> "Error"
    Status.CANCELLED -> "Cancelado"
}

@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
