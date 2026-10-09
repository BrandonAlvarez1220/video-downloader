package com.brandon.videodownloader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencias del usuario sobre SharedPreferences (un key-value persistente).
 * Cada ajuste se expone como StateFlow para que la UI y los workers vean el valor actual.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    enum class ThemeMode { SYSTEM, DARK, LIGHT }

    private val _defaultQuality = MutableStateFlow(prefs.getString(K_QUALITY, "1080")!!)
    val defaultQuality: StateFlow<String> = _defaultQuality.asStateFlow()

    private val _maxConcurrent = MutableStateFlow(prefs.getInt(K_CONCURRENT, 3))
    val maxConcurrent: StateFlow<Int> = _maxConcurrent.asStateFlow()

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(K_WIFI, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _sponsorBlock = MutableStateFlow(prefs.getBoolean(K_SPONSOR, true))
    val sponsorBlock: StateFlow<Boolean> = _sponsorBlock.asStateFlow()

    private val _embedMetadata = MutableStateFlow(prefs.getBoolean(K_METADATA, true))
    val embedMetadata: StateFlow<Boolean> = _embedMetadata.asStateFlow()

    private val _privateMode = MutableStateFlow(prefs.getBoolean(K_PRIVATE, false))
    val privateMode: StateFlow<Boolean> = _privateMode.asStateFlow()

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(K_THEME, null)!!) }.getOrDefault(ThemeMode.SYSTEM)
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setDefaultQuality(v: String) { _defaultQuality.value = v; prefs.edit().putString(K_QUALITY, v).apply() }
    fun setMaxConcurrent(v: Int) { _maxConcurrent.value = v; prefs.edit().putInt(K_CONCURRENT, v).apply() }
    fun setWifiOnly(v: Boolean) { _wifiOnly.value = v; prefs.edit().putBoolean(K_WIFI, v).apply() }
    fun setSponsorBlock(v: Boolean) { _sponsorBlock.value = v; prefs.edit().putBoolean(K_SPONSOR, v).apply() }
    fun setEmbedMetadata(v: Boolean) { _embedMetadata.value = v; prefs.edit().putBoolean(K_METADATA, v).apply() }
    fun setPrivateMode(v: Boolean) { _privateMode.value = v; prefs.edit().putBoolean(K_PRIVATE, v).apply() }
    fun setTheme(v: ThemeMode) { _theme.value = v; prefs.edit().putString(K_THEME, v.name).apply() }

    private companion object {
        const val K_QUALITY = "default_quality"
        const val K_CONCURRENT = "max_concurrent"
        const val K_WIFI = "wifi_only"
        const val K_SPONSOR = "sponsorblock"
        const val K_METADATA = "embed_metadata"
        const val K_THEME = "theme"
        const val K_PRIVATE = "private_mode"
    }
}
