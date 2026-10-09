package com.brandon.videodownloader.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.brandon.videodownloader.engine.Accounts
import com.brandon.videodownloader.ui.MainViewModel
import com.brandon.videodownloader.ui.components.GradientButton
import com.brandon.videodownloader.ui.components.GradientIcon
import com.brandon.videodownloader.ui.components.SectionHeader

/** Sitio a mostrar en el navegador: uno conocido o una URL que escribiste. */
private data class LoginTarget(val name: String, val url: String, val custom: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var target by remember { mutableStateOf<LoginTarget?>(null) }
    // Contador para forzar el recálculo de "sesión iniciada" al volver del navegador.
    var refresh by remember { mutableIntStateOf(0) }

    BackHandler { if (target != null) target = null else onClose() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(target?.name ?: "Cuentas y sesiones") },
            navigationIcon = {
                IconButton(onClick = { if (target != null) target = null else onClose() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        val t = target
        if (t != null) {
            LoginBrowser(t.url, Modifier.weight(1f))
            Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                GradientButton(
                    text = "Listo, guardar sesión",
                    icon = Icons.Filled.CheckCircle,
                    onClick = {
                        if (t.custom) Accounts.addCustomHost(context, t.url)
                        val n = Accounts.export(context)
                        vm.toast(if (n > 0) "Sesión guardada en ${t.name}" else "No se encontró una sesión; ¿terminaste de iniciar sesión?")
                        refresh++
                        target = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@Column
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradientIcon(Icons.Filled.Shield)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Inicia sesión para bajar videos con restricción de edad (+18), privados o de " +
                            "suscriptores. Tu sesión se guarda solo en este teléfono; la app nunca ve tu contraseña.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Sitios")
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // `refresh` se lee aquí para que Compose recalcule el estado tras guardar.
                    refresh.let { _ ->
                        Accounts.sites.forEach { site ->
                            val logged = Accounts.isLoggedIn(site)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { target = LoginTarget(site.name, site.loginUrl, custom = false) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(site.color))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(site.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (logged) "Sesión iniciada" else "Sin sesión",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (logged) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (logged) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                                else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        }
                    }
                }
            }

            SectionHeader("Otro sitio", Modifier.padding(top = 8.dp))
            var customUrl by remember { mutableStateOf("") }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "¿Un sitio que no está en la lista (p. ej. uno premium)? Escribe su dirección e inicia sesión ahí.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Public, null) },
                        placeholder = { Text("https://sitio.com") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    OutlinedButton(
                        onClick = {
                            val url = customUrl.trim().let { if (it.startsWith("http")) it else "https://$it" }
                            target = LoginTarget(url.removePrefix("https://").substringBefore('/'), url, custom = true)
                        },
                        enabled = customUrl.isNotBlank(),
                    ) { Text("Abrir e iniciar sesión") }
                }
            }

            TextButton(
                onClick = {
                    Accounts.logoutAll(context)
                    refresh++
                    vm.toast("Se cerraron todas las sesiones")
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.Logout, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar todas las sesiones", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/**
 * Navegador para iniciar sesión. Detalle: Google bloquea el login en "WebViews" detectándolos por
 * el texto "; wv" del user-agent; quitándolo, se comporta como Chrome normal.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginBrowser(url: String, modifier: Modifier = Modifier) {
    var progress by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
    }
}
