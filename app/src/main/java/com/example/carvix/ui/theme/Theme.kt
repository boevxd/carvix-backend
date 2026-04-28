package com.example.carvix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.carvix.ui.DarkPalette
import com.example.carvix.ui.EnStrings
import com.example.carvix.ui.LightPalette
import com.example.carvix.ui.LocalCarvixPalette
import com.example.carvix.ui.LocalStrings
import com.example.carvix.ui.RuStrings

@Composable
fun CarvixTheme(
    darkTheme: Boolean = false,
    language: String = "ru",
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val strings = if (language == "en") EnStrings else RuStrings

    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentOn,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentOn,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary
        )
    }

    CompositionLocalProvider(
        LocalCarvixPalette provides palette,
        LocalStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = Typography,
            content = content
        )
    }
}
