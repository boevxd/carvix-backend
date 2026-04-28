package com.example.carvix.data.repository

import android.content.Context
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("carvix_settings", Context.MODE_PRIVATE)

    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", false)
    fun setDarkTheme(value: Boolean) = prefs.edit { putBoolean("dark_theme", value) }

    fun getLanguage(): String = prefs.getString("language", "ru") ?: "ru"
    fun setLanguage(value: String) = prefs.edit { putString("language", value) }
}
