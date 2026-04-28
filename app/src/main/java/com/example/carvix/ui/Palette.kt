package com.example.carvix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CarvixPalette(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardSoft: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentOn: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val statusNew: Color,
    val statusInProgress: Color,
    val statusDone: Color,
    val statusRejected: Color,
    val statusWait: Color,
    val isDark: Boolean
)

val LightPalette = CarvixPalette(
    bg = Color(0xFFF5EFE6),
    surface = Color(0xFFFDFBF7),
    card = Color(0xFFEDE6DA),
    cardSoft = Color(0xFFF2EBDD),
    border = Color(0xFFE8E0D4),
    textPrimary = Color(0xFF3E3228),
    textSecondary = Color(0xFF5A4D42),
    textMuted = Color(0xFF8A8279),
    accent = Color(0xFF1F1F1F),
    accentOn = Color(0xFFFFFFFF),
    danger = Color(0xFFB00020),
    success = Color(0xFF2E7D32),
    warning = Color(0xFFE5A100),
    info = Color(0xFF3F6FB0),
    statusNew = Color(0xFF3F6FB0),
    statusInProgress = Color(0xFFE5A100),
    statusDone = Color(0xFF2E7D32),
    statusRejected = Color(0xFFB00020),
    statusWait = Color(0xFF8E5A28),
    isDark = false
)

val DarkPalette = CarvixPalette(
    bg = Color(0xFF14110E),
    surface = Color(0xFF1E1A16),
    card = Color(0xFF2A241E),
    cardSoft = Color(0xFF231F1A),
    border = Color(0xFF3A332B),
    textPrimary = Color(0xFFF1E9DC),
    textSecondary = Color(0xFFC9BEAB),
    textMuted = Color(0xFF8E8474),
    accent = Color(0xFFEDE3CC),
    accentOn = Color(0xFF14110E),
    danger = Color(0xFFFF6B6B),
    success = Color(0xFF66D26B),
    warning = Color(0xFFFFC062),
    info = Color(0xFF7FA8E0),
    statusNew = Color(0xFF7FA8E0),
    statusInProgress = Color(0xFFFFC062),
    statusDone = Color(0xFF66D26B),
    statusRejected = Color(0xFFFF6B6B),
    statusWait = Color(0xFFD4A36B),
    isDark = true
)

val LocalCarvixPalette = staticCompositionLocalOf { LightPalette }

object CarvixColors {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.surface
    val Card: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.card
    val CardSoft: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.cardSoft
    val Border: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.border
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.textMuted
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.accent
    val AccentOn: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.accentOn
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.danger
    val Success: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.warning
    val Info: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.info
    val StatusNew: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.statusNew
    val StatusInProgress: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.statusInProgress
    val StatusDone: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.statusDone
    val StatusRejected: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.statusRejected
    val StatusWait: Color @Composable @ReadOnlyComposable get() = LocalCarvixPalette.current.statusWait
}

@Composable
@ReadOnlyComposable
fun statusColor(statusId: Int?): Color = when (statusId) {
    1 -> CarvixColors.StatusNew
    2 -> CarvixColors.StatusInProgress
    3 -> CarvixColors.StatusDone
    4 -> CarvixColors.StatusRejected
    5 -> CarvixColors.StatusWait
    else -> CarvixColors.TextMuted
}

@Composable
@ReadOnlyComposable
fun statusLabel(statusId: Int?): String {
    val s = LocalStrings.current
    return when (statusId) {
        1 -> s.statusNew
        2 -> s.statusInProgress
        3 -> s.statusDone
        4 -> s.statusRejected
        5 -> s.statusWaitParts
        else -> "—"
    }
}

@Composable
@ReadOnlyComposable
fun priorityLabel(p: Int?): String {
    val s = LocalStrings.current
    return when (p) {
        1 -> s.priorityCritical
        2 -> s.priorityHigh
        3 -> s.priorityMedium
        4 -> s.priorityLow
        5 -> s.priorityPlanned
        else -> "—"
    }
}

@Composable
@ReadOnlyComposable
fun priorityColor(p: Int?): Color = when (p) {
    1 -> CarvixColors.Danger
    2 -> Color(0xFFE5681E)
    3 -> CarvixColors.Warning
    4 -> CarvixColors.Info
    else -> CarvixColors.TextMuted
}

@Composable
@ReadOnlyComposable
fun roleLabel(rolId: Int?): String {
    val s = LocalStrings.current
    return when (rolId) {
        1 -> if (s == EnStrings) "Analyst" else "Аналитик"
        2 -> if (s == EnStrings) "Dispatcher" else "Диспетчер"
        3 -> s.mechanic
        4 -> s.headMechanic
        5 -> if (s == EnStrings) "Director" else "Директор"
        else -> if (s == EnStrings) "Employee" else "Сотрудник"
    }
}
