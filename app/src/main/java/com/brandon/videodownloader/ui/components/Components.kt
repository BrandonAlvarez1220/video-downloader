package com.brandon.videodownloader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.brandon.videodownloader.ui.Platform
import com.brandon.videodownloader.ui.theme.Brand

/** Botón principal con el degradado de marca. Se atenúa si está deshabilitado. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(MaterialTheme.shapes.medium)
            .background(Brand.gradient)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else if (icon != null) {
                Icon(icon, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Pastilla de estado: punto de color + texto (En cola, Descargando, Error…). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

/** Insignia de plataforma (YouTube, TikTok…) para poner sobre una miniatura. */
@Composable
fun PlatformBadge(platform: Platform, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(platform.color))
        Spacer(Modifier.width(5.dp))
        Text(platform.name, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** Etiqueta oscura para duración / resolución sobre la miniatura. */
@Composable
fun OverlayLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Miniatura con placeholder animado mientras carga y un ícono si no hay imagen
 * (p. ej. sin internet o sitio sin miniatura).
 */
@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier, isAudio: Boolean = false) {
    val fallback = @Composable {
        Box(
            Modifier.fillMaxSize().background(Brand.gradientSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isAudio) Icons.Filled.MusicNote else Icons.Filled.Movie, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (url == null) fallback() else SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { Shimmer(Modifier.fillMaxSize()) },
            error = { fallback() },
        )
    }
}

/** Efecto "shimmer": brillo que recorre un bloque mientras algo carga (skeleton loading). */
@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    BoxWithConstraints(modifier.background(base)) {
        val w = maxWidth
        Box(
            Modifier
                .fillMaxHeight()
                .width(w / 2)
                .offset(x = w * x - w / 4)
                .background(Brush.horizontalGradient(listOf(base, highlight, base)))
        )
    }
}

/** Barra de progreso gruesa con degradado y animación suave entre valores. */
@Composable
fun GradientProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f), tween(450, easing = FastOutSlowInEasing), label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(Brand.gradient)
        )
    }
}

/** Barra indeterminada (para "Procesando"): un segmento que va y viene. */
@Composable
fun IndeterminateBar(modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val x by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "x",
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val segment = maxWidth * 0.35f
        Box(
            Modifier
                .fillMaxHeight()
                .width(segment)
                .offset(x = (maxWidth - segment) * x)
                .clip(CircleShape)
                .background(Brand.gradient)
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Ícono dentro de un "squircle" con degradado: logo de la app y estados vacíos. */
@Composable
fun GradientIcon(icon: ImageVector, size: Dp = 40.dp, iconSize: Dp = 22.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brand.gradient),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Brand.gradientSoft),
                contentAlignment = Alignment.Center,
            ) { GradientIcon(icon, size = 56.dp, iconSize = 28.dp) }
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}
