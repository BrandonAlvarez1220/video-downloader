package com.brandon.videodownloader.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brandon.videodownloader.data.Download
import com.brandon.videodownloader.data.Status
import com.brandon.videodownloader.engine.Quality
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.Platform
import com.brandon.videodownloader.ui.components.EmptyState
import com.brandon.videodownloader.ui.components.GradientProgressBar
import com.brandon.videodownloader.ui.components.IndeterminateBar
import com.brandon.videodownloader.ui.components.PlatformBadge
import com.brandon.videodownloader.ui.components.SectionHeader
import com.brandon.videodownloader.ui.components.StatusPill
import com.brandon.videodownloader.ui.components.Thumbnail
import com.brandon.videodownloader.ui.theme.Brand

@Composable
fun QueueScreen(vm: MainViewModel, queue: List<Download>) {
    if (queue.isEmpty()) {
        EmptyState(
            Icons.Filled.Downloading,
            "Nada en la cola",
            "Lo que mandes a descargar aparecerá aquí con su progreso, aunque cierres la app.",
            action = { TextButton(onClick = { vm.tab = 0 }) { Text("Pegar un enlace") } },
        )
        return
    }
    val active = queue.filter { it.status.isActive }
    val finished = queue.filter { !it.status.isActive }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Descargas", style = MaterialTheme.typography.headlineMedium) }
        if (active.isNotEmpty()) {
            item {
                val running = active.count { it.status != Status.QUEUED }
                SectionHeader("En progreso · $running activas · ${active.size - running} en espera")
            }
            // `key` le dice a Compose qué fila es cuál: solo redibuja la que cambió de progreso.
            items(active, key = { it.id }) { d -> DownloadCard(d, vm, Modifier.animateItem()) }
        }
        if (finished.isNotEmpty()) {
            item {
                SectionHeader("Con problemas", Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { vm.clearFailures() }) { Text("Limpiar todo") }
                }
            }
            items(finished, key = { it.id }) { d -> SwipeToRemove(onRemove = { vm.remove(d.id) }, modifier = Modifier.animateItem()) { DownloadCard(d, vm) } }
        }
    }
}

/** Desliza la tarjeta hacia la izquierda para quitarla (gesto estándar en apps modernas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) onRemove()
        it == SwipeToDismissBoxValue.EndToStart
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, "Quitar", tint = MaterialTheme.colorScheme.error) }
        },
    ) { content() }
}

@Composable
private fun DownloadCard(d: Download, vm: MainViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = d.status == Status.ERROR) { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Thumbnail(
                        d.thumbnail,
                        Modifier.size(width = 112.dp, height = 63.dp).clip(MaterialTheme.shapes.small),
                        isAudio = d.quality == Quality.AUDIO,
                    )
                    PlatformBadge(
                        Platform.fromSite(d.site, d.url),
                        Modifier.align(Alignment.BottomStart).padding(4.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        d.title ?: d.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (label, color) = statusStyle(d.status)
                        StatusPill(label, color)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Quality.label(d.quality), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (d.status.isActive) {
                    FilledTonalIconButton(onClick = { vm.cancel(d.id) }) { Icon(Icons.Filled.Close, "Cancelar") }
                } else {
                    FilledTonalIconButton(onClick = { vm.retry(d.id) }) { Icon(Icons.Filled.Refresh, "Reintentar") }
                }
            }

            when (d.status) {
                Status.DOWNLOADING -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradientProgressBar(d.progress / 100f)
                    Row {
                        Text(
                            "${d.progress.toInt()}%", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        d.progressText?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Status.PROCESSING -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IndeterminateBar()
                    Text(
                        d.progressText ?: "Procesando…", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Status.ERROR -> d.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun statusStyle(s: Status): Pair<String, Color> = when (s) {
    Status.QUEUED -> "En espera" to MaterialTheme.colorScheme.onSurfaceVariant
    Status.DOWNLOADING -> "Descargando" to MaterialTheme.colorScheme.primary
    Status.PROCESSING -> "Procesando" to Brand.Orange
    Status.COMPLETED -> "Listo" to Brand.Green
    Status.ERROR -> "Error" to MaterialTheme.colorScheme.error
    Status.CANCELLED -> "Cancelado" to MaterialTheme.colorScheme.onSurfaceVariant
}
