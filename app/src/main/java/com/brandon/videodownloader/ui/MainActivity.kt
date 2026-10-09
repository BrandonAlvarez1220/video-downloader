package com.brandon.videodownloader.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brandon.videodownloader.data.Status
import com.brandon.videodownloader.engine.Engine
import com.brandon.videodownloader.ui.screens.DownloadScreen
import com.brandon.videodownloader.ui.screens.LibraryScreen
import com.brandon.videodownloader.ui.screens.QueueScreen
import com.brandon.videodownloader.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { AppTheme { MainScreen(vm) } }
    }

    // launchMode="singleTop": si la app ya está abierta y compartes otro enlace, llega aquí.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::onSharedText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val queue by vm.queue.collectAsStateWithLifecycle()
    val engine by vm.engineState.collectAsStateWithLifecycle()
    val activeCount = queue.count { it.status.isActive }
    val failedCount = queue.count { it.status == Status.ERROR }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Android 13+: hay que pedir permiso para mostrar la notificación de progreso.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Downloader") },
                actions = { OverflowMenu(engine, onUpdate = vm::updateEngine) },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == 0, onClick = { vm.tab = 0 },
                    icon = { Icon(Icons.Filled.Download, null) }, label = { Text("Descargar") },
                )
                NavigationBarItem(
                    selected = vm.tab == 1, onClick = { vm.tab = 1 },
                    icon = {
                        BadgedBox(badge = {
                            if (activeCount > 0) Badge { Text("$activeCount") }
                            else if (failedCount > 0) Badge { Text("!") }
                        }) { Icon(Icons.Filled.Downloading, null) }
                    },
                    label = { Text("Cola") },
                )
                NavigationBarItem(
                    selected = vm.tab == 2, onClick = { vm.tab = 2 },
                    icon = { Icon(Icons.Filled.VideoLibrary, null) }, label = { Text("Biblioteca") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.tab) {
                0 -> DownloadScreen(vm, engine)
                1 -> QueueScreen(vm, queue)
                else -> LibraryScreen(vm)
            }
        }
    }
}

@Composable
private fun OverflowMenu(engine: Engine.State, onUpdate: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        val version = (engine as? Engine.State.Ready)?.version ?: "—"
        DropdownMenuItem(
            text = { Text("Actualizar yt-dlp (actual: $version)") },
            onClick = { open = false; onUpdate() },
            enabled = engine is Engine.State.Ready,
        )
    }
}
