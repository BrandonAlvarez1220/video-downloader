package com.brandon.videodownloader.ui.screens

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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brandon.videodownloader.ui.components.GradientButton
import com.brandon.videodownloader.ui.components.GradientIcon
import com.brandon.videodownloader.ui.theme.Brand

/** Pantalla que tapa todo mientras la app está bloqueada. Lanza la huella automáticamente. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(Brand.gradientSoft),
                contentAlignment = Alignment.Center,
            ) { GradientIcon(Icons.Filled.Lock, size = 72.dp, iconSize = 36.dp) }
            Spacer(Modifier.height(8.dp))
            Text("Video Downloader está bloqueada", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "Identifícate para ver tus descargas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            GradientButton(
                text = "Desbloquear",
                icon = Icons.Filled.Fingerprint,
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
