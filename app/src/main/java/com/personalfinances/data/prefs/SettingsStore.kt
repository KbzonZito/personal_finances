package com.personalfinances.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personalfinances.domain.Currency
import com.personalfinances.domain.ExchangeRates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val mainCurrency: Currency = Currency.CLP,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Consentimiento explícito del usuario para leer notificaciones. */
    val detectionEnabled: Boolean = false,
    val watchedPackages: Set<String> = emptySet(),
    val rates: ExchangeRates = ExchangeRates.DEFAULT,
    val ratesUpdatedAt: Long = 0,
    val hideAmounts: Boolean = false,
    /** Pedir huella/PIN al abrir la app. */
    val lockEnabled: Boolean = false,
    /** Minutos en segundo plano antes de volver a pedir desbloqueo (0 = siempre). */
    val lockTimeoutMinutes: Int = 1,
    /** FLAG_SECURE: oculta la app en recientes y bloquea capturas de pantalla. */
    val secureScreen: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val currency = stringPreferencesKey("main_currency")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val detection = booleanPreferencesKey("detection_enabled")
        val packages = stringSetPreferencesKey("watched_packages")
        val rates = stringPreferencesKey("rates")
        val ratesUpdated = stringPreferencesKey("rates_updated")
        val hide = booleanPreferencesKey("hide_amounts")
        val lock = booleanPreferencesKey("lock_enabled")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val secure = booleanPreferencesKey("secure_screen")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            mainCurrency = Currency.fromCode(p[K.currency] ?: "CLP"),
            themeMode = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[K.dynamic] ?: false,
            detectionEnabled = p[K.detection] ?: false,
            watchedPackages = p[K.packages] ?: emptySet(),
            rates = p[K.rates]?.let { decodeRates(it) } ?: ExchangeRates.DEFAULT,
            ratesUpdatedAt = p[K.ratesUpdated]?.toLongOrNull() ?: 0,
            hideAmounts = p[K.hide] ?: false,
            lockEnabled = p[K.lock] ?: false,
            lockTimeoutMinutes = p[K.lockTimeout] ?: 1,
            secureScreen = p[K.secure] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setMainCurrency(c: Currency) = context.dataStore.edit { it[K.currency] = c.code }
    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[K.theme] = t.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[K.dynamic] = v }
    suspend fun setDetectionEnabled(v: Boolean) = context.dataStore.edit { it[K.detection] = v }
    suspend fun setHideAmounts(v: Boolean) = context.dataStore.edit { it[K.hide] = v }
    suspend fun setLockEnabled(v: Boolean) = context.dataStore.edit { it[K.lock] = v }
    suspend fun setLockTimeout(minutes: Int) = context.dataStore.edit { it[K.lockTimeout] = minutes }
    suspend fun setSecureScreen(v: Boolean) = context.dataStore.edit { it[K.secure] = v }
    suspend fun setWatchedPackages(v: Set<String>) = context.dataStore.edit { it[K.packages] = v }

    suspend fun setRates(rates: ExchangeRates) = context.dataStore.edit {
        it[K.rates] = Json.encodeToString(rates.clpPerUnit.mapKeys { (k, _) -> k.code } as Map<String, Double>)
        it[K.ratesUpdated] = System.currentTimeMillis().toString()
    }

    private fun decodeRates(s: String): ExchangeRates = runCatching {
        val map = Json.decodeFromString<Map<String, Double>>(s)
        ExchangeRates(Currency.entries.associateWith { c -> map[c.code] ?: c.defaultRateClp })
    }.getOrDefault(ExchangeRates.DEFAULT)
}
