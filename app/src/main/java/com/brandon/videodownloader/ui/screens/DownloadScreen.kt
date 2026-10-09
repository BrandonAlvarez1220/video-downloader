package com.brandon.videodownloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.brandon.videodownloader.engine.Engine
import com.brandon.videodownloader.engine.ProbeResult
import com.brandon.videodownloader.engine.Quality
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.MainViewModel.Analysis
import com.brandon.videodownloader.ui.formatDuration

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(vm: MainViewModel, engine: Engine.State) {
    val clipboard = LocalClipboardManager.current
    val urls = vm.urls
    val busy = vm.busyMessage != null

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EngineBanner(engine)

        OutlinedTextField(
            value = vm.input,
            onValueChange = vm::onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enlaces (uno o varios)") },
            placeholder = { Text("https://…") },
            minLines = 3,
            maxLines = 8,
            trailingIcon = {
                if (vm.input.isEmpty()) {
                    IconButton(onClick = { clipboard.getText()?.text?.let(vm::onInputChange) }) {
                        Icon(Icons.Filled.ContentPaste, "Pegar")
                    }
                } else {
                    IconButton(onClick = { vm.onInputChange("") }) { Icon(Icons.Filled.Clear, "Borrar") }
                }
            },
            supportingText = {
                Text(
                    when (urls.size) {
                        0 -> "Tip: en YouTube, TikTok, Instagram… toca Compartir → Video Downloader"
                        1 -> "1 enlace detectado"
                        else -> "${urls.size} enlaces detectados"
                    }
                )
            },
        )

        if (urls.size == 1) {
            OutlinedButton(
                onClick = vm::analyze,
                enabled = !busy && vm.analysis !is Analysis.Loading && engine is Engine.State.Ready,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Analizar (ver resoluciones)")
            }
        }

        AnalysisCard(vm.analysis)

        Text("Calidad", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quality.all.forEach { q ->
                FilterChip(
                    selected = vm.quality == q,
                    onClick = { vm.quality = q },
                    label = { Text(Quality.label(q)) },
                )
            }
        }
        Text(
            "Si el video no tiene la resolución elegida, se baja la más alta que no la supere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = vm::downloadAll,
            enabled = urls.isNotEmpty() && !busy && engine is Engine.State.Ready,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(vm.busyMessage ?: "")
            } else {
                Icon(Icons.Filled.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (urls.size > 1) "Descargar ${urls.size}" else "Descargar")
            }
        }
    }
}

@Composable
private fun EngineBanner(engine: Engine.State) {
    when (engine) {
        is Engine.State.Ready -> Unit
        Engine.State.Initializing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Preparando el motor… (la primera vez tarda unos segundos)")
        }
        is Engine.State.Failed -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text("No se pudo iniciar el motor: ${engine.message}", Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun AnalysisCard(analysis: Analysis) {
    when (analysis) {
        Analysis.Idle -> Unit
        Analysis.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Analizando enlace…")
        }
        is Analysis.Failed -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null)
                Spacer(Modifier.width(12.dp))
                Text(analysis.message, style = MaterialTheme.typography.bodySmall)
            }
        }
        is Analysis.Done -> when (val r = analysis.result) {
            is ProbeResult.Video -> VideoPreview(r)
            is ProbeResult.Playlist -> Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(r.title ?: "Lista de reproducción", fontWeight = FontWeight.SemiBold)
                        Text("${r.entries.size} videos — se descargarán todos", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(v: ProbeResult.Video) {
    Card(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = v.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(v.title ?: v.url, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(v.uploader, v.site, formatDuration(v.durationSec).ifEmpty { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (v.heights.isNotEmpty()) {
                Text(
                    "Disponible: " + v.heights.take(8).joinToString(", ") { "${it}p" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
