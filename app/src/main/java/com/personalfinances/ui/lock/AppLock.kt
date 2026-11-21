package com.personalfinances.ui.lock

import android.os.SystemClock
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.personalfinances.data.prefs.Settings
import com.personalfinances.data.prefs.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado del bloqueo, compartido por todas las actividades del proceso.
 * Se vuelve a bloquear cuando la app estuvo en segundo plano más del tiempo configurado.
 */
object AppLock {
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private var lastUnlockAt = 0L
    private var lastBackgroundAt = 0L
    private var authInProgress = false

    fun onBackground() {
        if (!authInProgress) lastBackgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(settings: Settings) {
        if (!settings.lockEnabled || !_unlocked.value) return
        val wentAwayAfterUnlock = lastBackgroundAt > lastUnlockAt
        val awayMs = SystemClock.elapsedRealtime() - lastBackgroundAt
        if (wentAwayAfterUnlock && awayMs >= settings.lockTimeoutMinutes * 60_000L) _unlocked.value = false
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String = "Desbloquear Finanzas", onResult: (Boolean) -> Unit = {}) {
        if (authInProgress) return
        authInProgress = true
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                lastUnlockAt = SystemClock.elapsedRealtime()
                _unlocked.value = true
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
                onResult(false)
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Usa tu huella, rostro o el bloqueo del teléfono")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }
}

/** Aplica tema (para la ventana nativa) y FLAG_SECURE según los ajustes. */
@Composable
fun WindowSettingsEffect(activity: FragmentActivity, settings: Settings, loaded: Boolean) {
    LaunchedEffect(settings.secureScreen, loaded) {
        if (!loaded) return@LaunchedEffect
        if (settings.secureScreen) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    LaunchedEffect(settings.themeMode, loaded) {
        if (!loaded) return@LaunchedEffect
        val mode = when (settings.themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }
}

/** Muestra [content] solo si la app está desbloqueada (o si el bloqueo está desactivado). */
@Composable
fun LockGate(activity: FragmentActivity, settings: Settings, loaded: Boolean, content: @Composable () -> Unit) {
    val unlocked by AppLock.unlocked.collectAsState()
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, settings) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> AppLock.onForeground(settings)
                Lifecycle.Event.ON_STOP -> if (!activity.isChangingConfigurations) AppLock.onBackground()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    val state = when {
        !loaded -> 0
        !settings.lockEnabled || unlocked -> 2
        else -> 1
    }
    AnimatedContent(state, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith fadeOut() }, label = "lock") { s ->
        when (s) {
            0 -> Box(Modifier.fillMaxSize())
            1 -> LockScreen(activity)
            else -> content()
        }
    }
}

@Composable
private fun LockScreen(activity: FragmentActivity) {
    LaunchedEffect(Unit) { AppLock.authenticate(activity) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(20.dp))
            Text("Finanzas está bloqueada", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { AppLock.authenticate(activity) }) { Text("Desbloquear") }
        }
    }
}
