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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brandon.videodownloader.data.Download
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.MainViewModel.LibraryFilter
import com.brandon.videodownloader.ui.MainViewModel.LibrarySort
import com.brandon.videodownloader.ui.Platform
import com.brandon.videodownloader.ui.components.EmptyState
import com.brandon.videodownloader.ui.components.OverlayLabel
import com.brandon.videodownloader.ui.components.PlatformBadge
import com.brandon.videodownloader.ui.components.Thumbnail
import com.brandon.videodownloader.ui.formatBytes
import com.brandon.videodownloader.ui.formatDuration
import com.brandon.videodownloader.ui.formatResolution
import java.io.File

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val all by vm.library.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Download?>(null) }

    if (all.isEmpty()) {
        EmptyState(
            Icons.Filled.VideoLibrary,
            "Tu biblioteca está vacía",
            "Lo que descargues aparecerá aquí y en tu galería (Movies/VideoDownloader).",
            action = { TextButton(onClick = { vm.tab = 0 }) { Text("Descargar mi primer video") } },
        )
        return
    }

    val visible = vm.filterLibrary(all)
    val actions = ItemActions(
        play = { vm.overlay = MainViewModel.Overlay.Player(it) },
        openWith = { if (!openExternal(context, it)) vm.toast("No hay una app para abrir este archivo") },
        share = { share(context, it) },
        openSource = { openUrl(context, it.url) },
        delete = { toDelete = it },
    )

    LazyVerticalGrid(
        columns = if (vm.libraryGrid) GridCells.Adaptive(minSize = 160.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { LibraryHeader(vm, all) }
        if (visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nada coincide con tu búsqueda.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        }
        items(visible, key = { it.id }) { d ->
            if (vm.libraryGrid) GridItem(d, actions, Modifier.animateItem())
            else ListItem(d, actions, Modifier.animateItem())
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("¿Eliminar del teléfono?") },
            text = { Text("\"${d.title ?: d.fileName}\" se borrará de la app y de la galería.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFromLibrary(d); toDelete = null }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

private class ItemActions(
    val play: (Download) -> Unit,
    val openWith: (Download) -> Unit,
    val share: (Download) -> Unit,
    val openSource: (Download) -> Unit,
    val delete: (Download) -> Unit,
)

@Composable
private fun LibraryHeader(vm: MainViewModel, all: List<Download>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Biblioteca", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${all.size} archivos · ${formatBytes(all.sumOf { it.fileSize ?: 0 })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SortMenu(vm)
            IconButton(onClick = { vm.libraryGrid = !vm.libraryGrid }) {
                Icon(
                    if (vm.libraryGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    "Cambiar vista",
                )
            }
        }

        TextField(
            value = vm.search,
            onValueChange = { vm.search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Buscar por título, canal o sitio") },
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        // Filtros: tipo + cada sitio del que tengas descargas.
        val sites = all.mapNotNull { it.site }.distinct().sorted()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip("Todo", vm.libraryFilter == LibraryFilter.ALL && vm.librarySite == null) { vm.libraryFilter = LibraryFilter.ALL; vm.librarySite = null } }
            item { Chip("Videos", vm.libraryFilter == LibraryFilter.VIDEO) { vm.libraryFilter = LibraryFilter.VIDEO } }
            item { Chip("Audio", vm.libraryFilter == LibraryFilter.AUDIO) { vm.libraryFilter = LibraryFilter.AUDIO } }
            if (all.any { it.isPrivate }) {
                item { Chip("Privados", vm.libraryFilter == LibraryFilter.PRIVATE) { vm.libraryFilter = LibraryFilter.PRIVATE } }
            }
            items(sites) { site ->
                val p = Platform.fromSite(site, "")
                Chip(p.name, vm.librarySite == site, dot = p.color) {
                    vm.librarySite = if (vm.librarySite == site) null else site
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        leadingIcon = dot?.let { { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) } },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun SortMenu(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    onClick = { vm.librarySort = s; open = false },
                    trailingIcon = { if (vm.librarySort == s) Icon(Icons.Filled.Check, null) },
                )
            }
        }
    }
}

@Composable
private fun GridItem(d: Download, actions: ItemActions, modifier: Modifier = Modifier) {
    val isAudio = d.mimeType?.startsWith("audio/") == true
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { actions.play(d) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)) {
            Thumbnail(d.thumbnail, Modifier.fillMaxSize(), isAudio)
            PlayBadge(Modifier.align(Alignment.Center))
            PlatformBadge(Platform.fromSite(d.site, d.url), Modifier.align(Alignment.TopStart).padding(6.dp))
            if (d.isPrivate) PrivateBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            formatDuration(d.durationSec).takeIf { it.isNotEmpty() }?.let {
                OverlayLabel(it, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    d.title ?: d.fileName ?: d.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    meta(d, isAudio), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            ItemMenu(d, actions)
        }
    }
}

@Composable
private fun ListItem(d: Download, actions: ItemActions, modifier: Modifier = Modifier) {
    val isAudio = d.mimeType?.startsWith("audio/") == true
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { actions.play(d) },
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 128.dp, height = 72.dp).clip(MaterialTheme.shapes.small)) {
                Thumbnail(d.thumbnail, Modifier.fillMaxSize(), isAudio)
                if (d.isPrivate) PrivateBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
                formatDuration(d.durationSec).takeIf { it.isNotEmpty() }?.let {
                    OverlayLabel(it, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    d.title ?: d.fileName ?: d.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    listOfNotNull(d.uploader, Platform.fromSite(d.site, d.url).name).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(meta(d, isAudio), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            ItemMenu(d, actions)
        }
    }
}

private val Download.isPrivate get() = fileUri?.startsWith("file:") == true

/** Candado: el archivo está en la bóveda privada (no aparece en la galería). */
@Composable
private fun PrivateBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Lock, "Privado", tint = Color.White, modifier = Modifier.size(14.dp)) }
}

@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) }
}

@Composable
private fun ItemMenu(d: Download, actions: ItemActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.MoreVert, "Más", Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Reproducir", Icons.Filled.PlayArrow) { open = false; actions.play(d) }
            MenuItem("Abrir con…", Icons.AutoMirrored.Filled.OpenInNew) { open = false; actions.openWith(d) }
            MenuItem("Compartir", Icons.Filled.Share) { open = false; actions.share(d) }
            MenuItem("Ver publicación original", Icons.Filled.Language) { open = false; actions.openSource(d) }
            MenuItem("Eliminar", Icons.Filled.Delete, danger = true) { open = false; actions.delete(d) }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = { Icon(icon, null, tint = color) },
        onClick = onClick,
    )
}

private fun meta(d: Download, isAudio: Boolean) =
    listOf(if (isAudio) "MP3" else formatResolution(d.width, d.height), formatBytes(d.fileSize))
        .filter { it.isNotEmpty() }.joinToString(" · ")

/**
 * Los archivos del modo privado son `file://` internos: otras apps no pueden leerlos.
 * FileProvider genera un `content://` temporal con permiso SOLO para la app que elijas.
 */
private fun externalUri(context: Context, d: Download): Uri? {
    val uri = d.fileUri?.let(Uri::parse) ?: return null
    if (uri.scheme != "file") return uri
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(uri.path!!))
    }.getOrNull()
}

/** Abre el archivo con otra app (VLC, MX Player, Fotos…). */
private fun openExternal(context: Context, d: Download): Boolean {
    val uri = externalUri(context, d) ?: return false
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, d.mimeType ?: "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(intent, "Abrir con"))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private fun share(context: Context, d: Download) {
    val uri = externalUri(context, d) ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(d.mimeType ?: "video/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Compartir"))
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
