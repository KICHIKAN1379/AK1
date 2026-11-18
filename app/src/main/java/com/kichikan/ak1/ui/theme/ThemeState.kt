package com.kichikan.ak1.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Four complete visual worlds. Enum identifiers remain stable so existing saved
 * preferences migrate without losing the selected appearance.
 */
enum class ThemeMode(val label: String) {
    NEON("جنگلی"),
    ARCADE("جادویی"),
    MIDNIGHT("یخی"),
    AMOLED("سلطنتی")
}

object ThemeState {
    private const val PREFS = "ak1_settings"
    private const val KEY = "theme_mode"

    var mode by mutableStateOf(ThemeMode.NEON)
        private set

    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        mode = when (raw) {
            "FOREST" -> ThemeMode.NEON
            "MYSTIC" -> ThemeMode.ARCADE
            "FROST" -> ThemeMode.MIDNIGHT
            "ROYAL" -> ThemeMode.AMOLED
            else -> runCatching { ThemeMode.valueOf(raw.orEmpty()) }
                .getOrDefault(ThemeMode.NEON)
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
