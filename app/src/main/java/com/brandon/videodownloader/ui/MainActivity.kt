package com.brandon.videodownloader.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brandon.videodownloader.data.Status
import com.brandon.videodownloader.ui.components.GradientIcon
import com.brandon.videodownloader.ui.screens.AccountsScreen
import com.brandon.videodownloader.ui.screens.DownloadScreen
import com.brandon.videodownloader.ui.screens.LibraryScreen
import com.brandon.videodownloader.ui.screens.LockScreen
import com.brandon.videodownloader.ui.screens.PlayerScreen
import com.brandon.videodownloader.ui.screens.QueueScreen
import com.brandon.videodownloader.ui.screens.QuickDownloadContent
import com.brandon.videodownloader.ui.screens.SettingsScreen
import com.brandon.videodownloader.ui.theme.AppTheme

/**
 * FragmentActivity (y no ComponentActivity) porque el diálogo de huella del sistema
 * (BiometricPrompt) se monta como un Fragment.
 */
class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val theme by vm.settings.theme.collectAsStateWithLifecycle()
            val lockEnabled by vm.settings.appLock.collectAsStateWithLifecycle()
            // Con bloqueo activo: FLAG_SECURE oculta la app en "Recientes" (sale en negro)
            // e impide capturas de pantalla de su contenido.
            LaunchedEffect(lockEnabled) {
                if (lockEnabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            AppTheme(theme) {
                if (lockEnabled && AppLock.locked) LockScreen(onUnlock = { AppLock.unlock(this) })
                else Root(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForeground(this, vm.settings.appLock.value)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onBackground()
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

/** Raíz: pestañas principales + pantallas superpuestas (Ajustes, Reproductor) con transición. */
@Composable
private fun Root(vm: MainViewModel) {
    val engine by vm.engineState.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = vm.overlay,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
            else fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        contentKey = { it?.javaClass },
        label = "overlay",
    ) { overlay ->
        when (overlay) {
            null -> MainScreen(vm)
            MainViewModel.Overlay.Settings -> SettingsScreen(vm, engine, onClose = { vm.overlay = null })
            is MainViewModel.Overlay.Player -> PlayerScreen(overlay.download, onClose = { vm.overlay = null })
            MainViewModel.Overlay.Accounts -> AccountsScreen(vm, onClose = { vm.overlay = MainViewModel.Overlay.Settings })
        }
    }
}

private data class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("Descargar", Icons.Filled.Download, Icons.Outlined.Download),
    Tab("Cola", Icons.Filled.Downloading, Icons.Outlined.Downloading),
    Tab("Biblioteca", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
)

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientIcon(Icons.Filled.Download, size = 32.dp, iconSize = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Video Downloader", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.overlay = MainViewModel.Overlay.Settings }) {
                        Icon(Icons.Filled.Settings, "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEachIndexed { i, tab ->
                    val selected = vm.tab == i
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.tab = i },
                        label = { Text(tab.label) },
                        icon = {
                            BadgedBox(badge = {
                                if (i == 1 && activeCount > 0) Badge { Text("$activeCount") }
                                else if (i == 1 && failedCount > 0) Badge { Text("!") }
                            }) { Icon(if (selected) tab.selected else tab.unselected, null) }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = vm.tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabs",
            ) { tab ->
                when (tab) {
                    0 -> DownloadScreen(vm, engine)
                    1 -> QueueScreen(vm, queue)
                    else -> LibraryScreen(vm)
                }
            }
        }
    }

    // Hoja inferior al compartir un enlace desde otra app: eliges calidad y listo.
    if (vm.quickSheet) {
        ModalBottomSheet(
            onDismissRequest = vm::dismissQuickSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) { QuickDownloadContent(vm, engine) }
    }
}
