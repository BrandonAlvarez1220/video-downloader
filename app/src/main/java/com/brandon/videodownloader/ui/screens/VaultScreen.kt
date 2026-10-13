package com.brandon.videodownloader.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.brandon.videodownloader.ui.AppLock
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.components.GradientButton
import com.brandon.videodownloader.ui.components.GradientIcon
import com.brandon.videodownloader.ui.theme.Brand

/**
 * La Bóveda: una biblioteca aparte que pide huella/PIN cada vez que entras y se vuelve a
 * cerrar en cuanto sales de ella o de la app. Sus videos nunca aparecen en la biblioteca normal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val close = {
        AppLock.lockVault()
        onClose()
    }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Bóveda") },
            navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Salir de la Bóveda") } },
            actions = {
                if (AppLock.vaultUnlocked) {
                    IconButton(onClick = close) { Icon(Icons.Filled.Lock, "Cerrar Bóveda") }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        when {
            // Sin huella ni PIN en el teléfono no hay con qué proteger la Bóveda.
            !AppLock.canAuthenticate(context) -> VaultMessage(
                title = "Configura un bloqueo de pantalla",
                text = "Para usar la Bóveda tu teléfono necesita huella, rostro o un PIN/patrón.",
                button = "Abrir ajustes de seguridad",
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } },
            )
            !AppLock.vaultUnlocked -> {
                // Lanza la huella automáticamente al entrar.
                LaunchedEffect(Unit) { activity?.let(AppLock::unlockVault) }
                VaultMessage(
                    title = "Bóveda protegida",
                    text = "Identifícate para ver tus descargas privadas.",
                    button = "Desbloquear",
                    onClick = { activity?.let(AppLock::unlockVault) },
                )
            }
            else -> LibraryScreen(vm, vault = true)
        }
    }
}

@Composable
private fun VaultMessage(title: String, text: String, button: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(Brand.gradientSoft),
                contentAlignment = Alignment.Center,
            ) { GradientIcon(Icons.Filled.Lock, size = 72.dp, iconSize = 36.dp) }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            GradientButton(text = button, icon = Icons.Filled.Fingerprint, onClick = onClick, modifier = Modifier.fillMaxWidth())
        }
    }
}
