package com.example.carvix.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carvix.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    fullName: String,
    rolId: Int,
    login: String,
    isDark: Boolean,
    language: String,
    onToggleTheme: (Boolean) -> Unit,
    onChangeLanguage: (String) -> Unit,
    onLogout: () -> Unit
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current

    Scaffold(containerColor = palette.bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Аватарка + имя
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape).background(palette.card),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = fullName.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
                        Text(initials.ifBlank { "?" }, color = palette.textPrimary, fontWeight = FontWeight.Medium, fontSize = 28.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(fullName, color = palette.textPrimary, fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(roleLabel(rolId), color = palette.textMuted, fontSize = 13.sp)
                    if (login.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("@$login", color = palette.textMuted, fontSize = 12.sp)
                    }
                }
            }

            // Настройки
            Text(s.settings.uppercase(), color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
            ) {
                Column {
                    SettingRow(
                        icon = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = s.theme,
                        subtitle = if (isDark) s.themeDark else s.themeLight
                    ) {
                        Switch(
                            checked = isDark,
                            onCheckedChange = onToggleTheme,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.accent,
                                checkedTrackColor = palette.accent.copy(alpha = 0.3f),
                                uncheckedThumbColor = palette.textMuted,
                                uncheckedTrackColor = palette.border
                            )
                        )
                    }
                    HorizontalDivider(color = palette.border, thickness = 0.5.dp)
                    LanguageRow(language = language, onChange = onChangeLanguage)
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.danger.copy(alpha = 0.1f), contentColor = palette.danger)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.logout, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit
) {
    val palette = LocalCarvixPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.card),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = palette.textPrimary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = palette.textMuted, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
private fun LanguageRow(language: String, onChange: (String) -> Unit) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val langLabel = if (language == "ru") s.russian else s.english
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.card),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Language, null, tint = palette.textPrimary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.language, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(langLabel, color = palette.textMuted, fontSize = 12.sp)
        }
        // Сегментированный переключатель ru/en
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(palette.card)
        ) {
            LangChip("RU", language == "ru") { onChange("ru") }
            LangChip("EN", language == "en") { onChange("en") }
        }
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalCarvixPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.accent else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) palette.accentOn else palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
