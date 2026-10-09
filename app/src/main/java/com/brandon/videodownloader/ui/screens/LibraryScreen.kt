package com.brandon.videodownloader.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.brandon.videodownloader.data.Download
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.formatBytes
import com.brandon.videodownloader.ui.formatDuration
import com.brandon.videodownloader.ui.formatResolution

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val all by vm.library.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Download?>(null) }

    val q = vm.search.trim()
    val items = if (q.isEmpty()) all else all.filter {
        listOfNotNull(it.title, it.uploader, it.site).any { s -> s.contains(q, ignoreCase = true) }
    }

    if (all.isEmpty()) {
        EmptyState(Icons.Filled.VideoLibrary, "Tu biblioteca está vacía", "Lo que descargues aparecerá aquí y en tu galería.")
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = vm.search, onValueChange = { vm.search = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Buscar por título, canal o sitio") },
            )
            Text(
                "${all.size} archivos · ${formatBytes(all.sumOf { it.fileSize ?: 0 })} · Galería › Movies/VideoDownloader",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(items, key = { it.id }) { d ->
            LibraryItem(
                d,
                onOpen = { if (!open(context, d)) vm.toast("No se pudo abrir (¿se borró desde la galería?)") },
                onShare = { share(context, d) },
                onDelete = { toDelete = d },
            )
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar?") },
            text = { Text("Se borrará \"${d.title ?: d.fileName}\" del teléfono.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFromLibrary(d); toDelete = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LibraryItem(d: Download, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val isAudio = d.mimeType?.startsWith("audio/") == true
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = d.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 120.dp, height = 68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (isAudio) {
                    Icon(Icons.Filled.MusicNote, null, Modifier.align(Alignment.Center), tint = Color.White)
                }
                formatDuration(d.durationSec).takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it, color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    d.title ?: d.fileName ?: d.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(d.uploader, d.site).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOf(if (isAudio) "MP3" else formatResolution(d.width, d.height), formatBytes(d.fileSize))
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column {
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Compartir") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Eliminar") }
            }
        }
    }
}

/** Abre el archivo con el reproductor que elijas (VLC, MX Player, Fotos…). */
private fun open(context: Context, d: Download): Boolean {
    val uri = d.fileUri?.let(Uri::parse) ?: return false
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, d.mimeType ?: "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun share(context: Context, d: Download) {
    val uri = d.fileUri?.let(Uri::parse) ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(d.mimeType ?: "video/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
