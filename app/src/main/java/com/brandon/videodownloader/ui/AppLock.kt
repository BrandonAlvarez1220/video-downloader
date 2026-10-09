package com.brandon.videodownloader.ui

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Bloqueo de la app con huella / rostro / PIN del teléfono.
 *
 * Por qué no guardamos un PIN propio: BiometricPrompt delega en el sistema. La huella nunca
 * llega a la app (vive en un chip seguro del teléfono); Android solo nos responde "sí, es el dueño".
 * Es más seguro y no hay contraseña que olvidar.
 *
 * Es un `object` (singleton del proceso) para que el estado sobreviva a rotaciones de pantalla.
 */
object AppLock {
    /** Si pasas menos de esto fuera de la app (p. ej. contestar un mensaje), no vuelve a pedir huella. */
    private const val GRACE_MS = 30_000L

    // Al arrancar el proceso empieza bloqueada; si el bloqueo está apagado se libera en onForeground.
    var locked by mutableStateOf(true)
        private set
    private var backgroundAt = 0L

    /** Huella/rostro débil o fuerte, o en su defecto PIN/patrón/contraseña del teléfono. */
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(context: Context, enabled: Boolean) {
        // Si quitaste la huella/PIN del teléfono no te dejamos fuera de tu propia app.
        if (!enabled || !canAuthenticate(context)) {
            locked = false
            return
        }
        if (backgroundAt != 0L && SystemClock.elapsedRealtime() - backgroundAt > GRACE_MS) locked = true
    }

    fun unlock(activity: FragmentActivity) {
        authenticate(activity, "Desbloquear Video Downloader") { locked = false }
    }

    /** Muestra el diálogo del sistema y llama a [onSuccess] solo si te identificas. */
    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Usa tu huella, rostro o el PIN del teléfono")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
