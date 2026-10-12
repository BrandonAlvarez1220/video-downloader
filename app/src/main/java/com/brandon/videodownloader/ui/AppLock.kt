package com.brandon.videodownloader.ui

import android.content.Context
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
 * Bloqueo con huella / rostro / PIN del teléfono, para la app completa y para la Bóveda.
 *
 * Por qué no guardamos un PIN propio: BiometricPrompt delega en el sistema. La huella nunca
 * llega a la app (vive en un chip seguro del teléfono); Android solo nos responde "sí, es el dueño".
 * Es más seguro y no hay contraseña que olvidar.
 *
 * Es un `object` (singleton del proceso) para que el estado sobreviva a rotaciones de pantalla.
 */
object AppLock {
    /** App completa bloqueada. Empieza en true: al abrir la app (proceso nuevo) siempre pide huella. */
    var locked by mutableStateOf(true)
        private set

    /** La Bóveda está abierta. Se cierra sola en cuanto sales de la app. */
    var vaultUnlocked by mutableStateOf(false)
        private set

    /** Hubo un salto a segundo plano desde la última vez que se vio la app. */
    private var wentToBackground = false

    /**
     * Mientras el diálogo de huella/PIN está abierto. Por qué importa: si eliges "usar PIN",
     * Android abre OTRA pantalla y nuestra app pasa a segundo plano; sin esta bandera, al volver
     * creería que saliste y volvería a pedir la huella (bucle infinito).
     */
    private var authInProgress = false

    /** Huella/rostro débil o fuerte, o en su defecto PIN/patrón/contraseña del teléfono. */
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onBackground() {
        if (authInProgress) return
        wentToBackground = true
        vaultUnlocked = false
    }

    fun onForeground(context: Context, enabled: Boolean) {
        // Si quitaste la huella/PIN del teléfono no te dejamos fuera de tu propia app.
        if (!enabled || !canAuthenticate(context)) {
            locked = false
        } else if (wentToBackground) {
            // Sin periodo de gracia: cada vez que vuelves (otra app, pantalla apagada, etc.) pide huella.
            locked = true
        }
        wentToBackground = false
    }

    fun unlock(activity: FragmentActivity) {
        authenticate(activity, "Desbloquear Video Downloader") { locked = false }
    }

    fun unlockVault(activity: FragmentActivity) {
        authenticate(activity, "Abrir la Bóveda") { vaultUnlocked = true }
    }

    fun lockVault() {
        vaultUnlocked = false
    }

    /** Muestra el diálogo del sistema y llama a [onSuccess] solo si te identificas. */
    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        authInProgress = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    onSuccess()
                }

                // Cancelado, demasiados intentos, etc. (un dedo no reconocido NO llega aquí: deja reintentar).
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                }
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
