package com.brandon.videodownloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brandon.videodownloader.BuildConfig
import com.brandon.videodownloader.data.Settings.ThemeMode
import com.brandon.videodownloader.engine.Engine
import com.brandon.videodownloader.engine.Quality
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, engine: Engine.State, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val s = vm.settings
    val context = LocalContext.current
    val defaultQuality by s.defaultQuality.collectAsStateWithLifecycle()
    val maxConcurrent by s.maxConcurrent.collectAsStateWithLifecycle()
    val wifiOnly by s.wifiOnly.collectAsStateWithLifecycle()
    val sponsor by s.sponsorBlock.collectAsStateWithLifecycle()
    val metadata by s.embedMetadata.collectAsStateWithLifecycle()
    val theme by s.theme.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Ajustes") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Descargas")
            Group {
                SettingRow(Icons.Filled.HighQuality, "Calidad predeterminada", Quality.label(defaultQuality)) {
                    QualityPicker(defaultQuality) { s.setDefaultQuality(it); vm.selectQuality(it) }
                }
                SettingRow(Icons.Filled.Speed, "Descargas simultáneas", "$maxConcurrent a la vez") {}
                Slider(
                    value = maxConcurrent.toFloat(),
                    onValueChange = { s.setMaxConcurrent(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingRow(Icons.Filled.Wifi, "Solo con Wi-Fi", "No gastar datos móviles (aplica a nuevas descargas)") {
                    Switch(wifiOnly, onCheckedChange = s::setWifiOnly)
                }
            }

            SectionHeader("Contenido", Modifier.padding(top = 8.dp))
            Group {
                SettingRow(
                    Icons.Filled.Block, "Quitar patrocinios (YouTube)",
                    "SponsorBlock recorta los anuncios que el youtuber mete DENTRO del video",
                ) { Switch(sponsor, onCheckedChange = s::setSponsorBlock) }
                SettingRow(
                    Icons.Filled.Label, "Metadatos y carátula",
                    "Guarda título, autor y portada dentro del archivo",
                ) { Switch(metadata, onCheckedChange = s::setEmbedMetadata) }
            }

            SectionHeader("Apariencia", Modifier.padding(top = 8.dp))
            Group {
                SettingRow(Icons.Filled.DarkMode, "Tema", null) {}
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                    val options = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.DARK to "Oscuro", ThemeMode.LIGHT to "Claro")
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = theme == mode,
                            onClick = { s.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
            }

            SectionHeader("Motor", Modifier.padding(top = 8.dp))
            Group {
                val version = (engine as? Engine.State.Ready)?.version ?: "—"
                SettingRow(Icons.Filled.SystemUpdate, "yt-dlp", "Versión $version · se actualiza solo cada día") {
                    FilledTonalButton(onClick = vm::updateEngine, enabled = engine is Engine.State.Ready && !vm.updatingEngine) {
                        if (vm.updatingEngine) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Actualizar")
                    }
                }
                SettingRow(
                    Icons.Filled.Public, "Sitios compatibles", "Ver la lista completa (+1800)",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md"))
                            )
                        }
                    },
                ) {}
            }

            SectionHeader("Acerca de", Modifier.padding(top = 8.dp))
            Group {
                SettingRow(Icons.Filled.Info, "Video Downloader", "Versión ${BuildConfig.VERSION_NAME} · uso personal") {}
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun QualityPicker(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }) { Text(Quality.label(current)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Quality.all.forEach { q ->
                DropdownMenuItem(
                    text = { Text(Quality.label(q)) },
                    onClick = { onPick(q); open = false },
                    trailingIcon = { if (q == current) Icon(Icons.Filled.Check, null) },
                )
            }
        }
    }
}
