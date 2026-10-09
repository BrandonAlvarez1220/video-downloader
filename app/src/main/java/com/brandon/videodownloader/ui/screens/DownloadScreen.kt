package com.brandon.videodownloader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brandon.videodownloader.engine.Engine
import com.brandon.videodownloader.engine.ProbeResult
import com.brandon.videodownloader.engine.Quality
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.MainViewModel.Analysis
import com.brandon.videodownloader.ui.Platform
import com.brandon.videodownloader.ui.components.GradientButton
import com.brandon.videodownloader.ui.components.GradientIcon
import com.brandon.videodownloader.ui.components.OverlayLabel
import com.brandon.videodownloader.ui.components.PlatformBadge
import com.brandon.videodownloader.ui.components.SectionHeader
import com.brandon.videodownloader.ui.components.Shimmer
import com.brandon.videodownloader.ui.components.Thumbnail
import com.brandon.videodownloader.ui.formatBytes
import com.brandon.videodownloader.ui.formatDuration
import com.brandon.videodownloader.ui.theme.Brand

@Composable
fun DownloadScreen(vm: MainViewModel, engine: Engine.State) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Descarga sin anuncios", style = MaterialTheme.typography.headlineMedium)
            Text(
                "YouTube, TikTok, Instagram, X, Facebook y +1800 sitios.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EngineBanner(engine)
        UrlInputCard(vm)
        PreviewSection(vm.analysis, onRetry = vm::retryAnalysis)

        AnimatedVisibility(vm.urls.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FormatSelector(vm)
                DownloadButton(vm, engine)
            }
        }

        if (vm.urls.isEmpty()) ShareTip()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DownloadButton(vm: MainViewModel, engine: Engine.State, modifier: Modifier = Modifier) {
    val n = vm.urls.size
    val playlist = ((vm.analysis as? Analysis.Done)?.result as? ProbeResult.Playlist)?.entries?.size
    GradientButton(
        text = vm.busyMessage ?: when {
            playlist != null -> "Descargar $playlist videos"
            n > 1 -> "Descargar $n enlaces"
            vm.isAudio -> "Descargar audio"
            else -> "Descargar video"
        },
        icon = Icons.Filled.Download,
        onClick = vm::downloadAll,
        enabled = n > 0 && engine is Engine.State.Ready,
        loading = vm.busyMessage != null,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun UrlInputCard(vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    val urls = vm.urls
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Enlaces", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (vm.input.isNotEmpty()) {
                    IconButton(onClick = { vm.onInputChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Borrar", Modifier.size(18.dp))
                    }
                }
            }
            TextField(
                value = vm.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Pega aquí uno o varios enlaces, uno por línea") },
                minLines = 2,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Insignias de las plataformas detectadas en lo que pegaste.
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    urls.map { Platform.fromUrl(it) }.distinct().take(3).forEach { PlatformBadge(it) }
                    if (urls.size > 1) {
                        Text(
                            "${urls.size} enlaces", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                FilledTonalButton(onClick = {
                    clipboard.getText()?.text?.let { pasted ->
                        vm.onInputChange(if (vm.input.isBlank()) pasted else vm.input.trimEnd() + "\n" + pasted)
                    }
                }) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pegar")
                }
            }
        }
    }
}

@Composable
fun PreviewSection(analysis: Analysis, onRetry: () -> Unit) {
    AnimatedContent(
        targetState = analysis,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "preview",
    ) { a ->
        when (a) {
            Analysis.Idle -> Spacer(Modifier.height(0.dp))
            Analysis.Loading -> PreviewSkeleton()
            is Analysis.Failed -> Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("No se pudo analizar", style = MaterialTheme.typography.titleSmall)
                        Text(a.message, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, "Reintentar") }
                }
            }
            is Analysis.Done -> when (val r = a.result) {
                is ProbeResult.Video -> VideoPreview(r)
                is ProbeResult.Playlist -> PlaylistPreview(r, a.url)
            }
        }
    }
}

@Composable
private fun VideoPreview(v: ProbeResult.Video) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large)
    ) {
        Thumbnail(v.thumbnail, Modifier.fillMaxSize())
        // "Scrim": degradado oscuro para que el texto blanco se lea sobre cualquier imagen.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
        )
        PlatformBadge(Platform.fromSite(v.site, v.url), Modifier.align(Alignment.TopStart).padding(12.dp))
        formatDuration(v.durationSec).takeIf { it.isNotEmpty() }?.let {
            OverlayLabel(it, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                v.title ?: v.url, color = Color.White, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            v.uploader?.let {
                Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlaylistPreview(p: ProbeResult.Playlist, url: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(Icons.AutoMirrored.Filled.PlaylistPlay, size = 52.dp, iconSize = 28.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.title ?: "Lista de reproducción", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    listOfNotNull(p.uploader, "${p.entries.size} videos").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlatformBadge(Platform.fromUrl(url), Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun PreviewSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Shimmer(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Analizando enlace…", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Selector Video/Audio + mosaicos de resolución con tamaño estimado. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormatSelector(vm: MainViewModel) {
    val video = ((vm.analysis as? Analysis.Done)?.result as? ProbeResult.Video)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Formato")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !vm.isAudio,
                onClick = { vm.setAudioMode(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { SegmentedButtonDefaults.Icon(!vm.isAudio) { Icon(Icons.Filled.Movie, null, Modifier.size(18.dp)) } },
            ) { Text("Video") }
            SegmentedButton(
                selected = vm.isAudio,
                onClick = { vm.setAudioMode(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { SegmentedButtonDefaults.Icon(vm.isAudio) { Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp)) } },
            ) { Text("Solo audio") }
        }

        if (vm.isAudio) {
            QualityTile(
                title = "MP3 · 192 kbps",
                subtitle = "Con carátula y metadatos",
                size = video?.audioSize?.let { "~" + formatBytes(it) },
                selected = true,
                available = true,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SectionHeader("Resolución")
            val heights = video?.heights.orEmpty()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                Quality.all.filter { it != Quality.AUDIO }.forEach { q ->
                    val h = q.toIntOrNull()
                    // Disponible si el sitio ofrece esa altura (±10 % para tolerar 1080 vs 1088, etc.).
                    val match = h?.let { target -> heights.firstOrNull { it in (target * 0.9).toInt()..(target * 1.1).toInt() } }
                    val available = video == null || heights.isEmpty() || h == null || match != null
                    val size = when {
                        h == null -> heights.firstOrNull()?.let { video?.sizes?.get(it) }
                        match != null -> video?.sizes?.get(match)
                        else -> null
                    }
                    QualityTile(
                        title = Quality.label(q),
                        subtitle = when {
                            h == null -> heights.firstOrNull()?.let { "${it}p" } ?: "La mejor"
                            !available -> "No disponible"
                            else -> qualityName(h)
                        },
                        size = size?.let { "~" + formatBytes(it) },
                        selected = vm.quality == q,
                        available = available,
                        onClick = { vm.selectQuality(q) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun qualityName(h: Int) = when {
    h >= 2160 -> "Ultra HD"
    h >= 1440 -> "Quad HD"
    h >= 1080 -> "Full HD"
    h >= 720 -> "HD"
    h >= 480 -> "SD"
    else -> "Ahorro de datos"
}

@Composable
private fun QualityTile(
    title: String,
    subtitle: String,
    size: String?,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val borderMod = if (selected) Modifier.border(2.dp, Brand.gradient, shape)
    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    Column(
        modifier
            .clip(shape)
            .then(borderMod)
            .background(if (selected) Brand.gradientSoft else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            title, style = MaterialTheme.typography.titleSmall,
            color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            subtitle, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (available) 1f else 0.5f),
            maxLines = 1,
        )
        if (size != null) {
            Text(size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EngineBanner(engine: Engine.State) {
    when (engine) {
        is Engine.State.Ready -> Unit
        Engine.State.Initializing -> Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Preparando el motor… la primera vez tarda unos segundos", style = MaterialTheme.typography.bodySmall)
            }
        }
        is Engine.State.Failed -> Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("No se pudo iniciar el motor: ${engine.message}", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShareTip() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brand.gradientSoft),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(Icons.Filled.Share)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Más rápido: desde la app", style = MaterialTheme.typography.titleSmall)
                Text(
                    "En YouTube, TikTok o Instagram toca Compartir → Video Downloader y elige la calidad sin salir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Contenido de la hoja inferior que aparece al compartir un enlace desde otra app. */
@Composable
fun QuickDownloadContent(vm: MainViewModel, engine: Engine.State) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Descarga rápida", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::dismissQuickSheet) { Text("Cerrar") }
        }
        PreviewSection(vm.analysis, onRetry = vm::retryAnalysis)
        FormatSelector(vm)
        DownloadButton(vm, engine)
    }
}
