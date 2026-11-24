package com.kichikan.ak1.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Complete visual presets used by the whole application. */
enum class ThemeMode(val label: String) {
    FOREST("جنگلی"),
    MYSTIC("جادویی"),
    FROST("یخی"),
    ROYAL("سلطنتی")
}

/**
 * Process-wide theme selection. The same preference key is retained so the setting
 * remains part of the existing local settings store and survives app restarts.
 */
object ThemeState {
    private const val PREFS = "ak1_settings"
    private const val KEY = "theme_mode"

    var mode by mutableStateOf(ThemeMode.FOREST)
        private set

    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        mode = when (raw) {
            "ARCADE" -> ThemeMode.MYSTIC
            "MIDNIGHT" -> ThemeMode.FROST
            "AMOLED" -> ThemeMode.ROYAL
            else -> runCatching { ThemeMode.valueOf(raw.orEmpty()) }
                .getOrDefault(ThemeMode.FOREST)
        }
        Neon.apply(mode)
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        Neon.apply(value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, value.name)
            .apply()
    }
}
