package com.twinpane.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    const val MODE_SYSTEM = 0
    const val MODE_DARK = 1
    const val MODE_LIGHT = 2

    fun getSavedThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences("twinpane_theme", Context.MODE_PRIVATE)
        return prefs.getInt("theme_mode", MODE_DARK) // Default to Royal Dark IDE
    }

    fun applyTheme(context: Context) {
        when (getSavedThemeMode(context)) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences("twinpane_theme", Context.MODE_PRIVATE)
            .edit()
            .putInt("theme_mode", mode)
            .apply()
        applyTheme(context)
    }

    fun showThemeSelectionDialog(context: Context, onThemeChanged: (() -> Unit)? = null) {
        val current = getSavedThemeMode(context)
        val items = listOf(
            CustomListItem(
                title = "Royal Dark IDE",
                subtitle = "Deep Royal Neon Theme (Recommended)",
                iconRes = R.drawable.ic_settings,
                badgeText = if (current == MODE_DARK) "Active" else null,
                action = {
                    setThemeMode(context, MODE_DARK)
                    onThemeChanged?.invoke()
                }
            ),
            CustomListItem(
                title = "Clean Light Studio",
                subtitle = "High Visibility Light Theme for Day Use",
                iconRes = R.drawable.ic_template,
                badgeText = if (current == MODE_LIGHT) "Active" else null,
                action = {
                    setThemeMode(context, MODE_LIGHT)
                    onThemeChanged?.invoke()
                }
            ),
            CustomListItem(
                title = "System Default",
                subtitle = "Automatically match Android OS System Theme",
                iconRes = R.drawable.ic_viewport,
                badgeText = if (current == MODE_SYSTEM) "Active" else null,
                action = {
                    setThemeMode(context, MODE_SYSTEM)
                    onThemeChanged?.invoke()
                }
            )
        )
        DialogUiHelper.showCustomListDialog(context, "App Theme Mode", items)
    }
}
