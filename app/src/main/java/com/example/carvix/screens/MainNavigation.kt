package com.example.carvix.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.carvix.data.repository.AppRepository
import com.example.carvix.data.repository.AuthRepository
import com.example.carvix.ui.LocalCarvixPalette
import com.example.carvix.ui.LocalStrings
import kotlinx.coroutines.delay

sealed class Route {
    data object Home : Route()
    data object Messages : Route()
    data object Profile : Route()
    data class ZayavkaDetails(val id: Int) : Route()
    data object CreateZayavka : Route()
    data class Chat(val userId: Int, val fio: String) : Route()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    authRepo: AuthRepository,
    isDark: Boolean,
    language: String,
    onToggleTheme: (Boolean) -> Unit,
    onChangeLanguage: (String) -> Unit,
    onLogout: () -> Unit
) {
    val repo = remember { AppRepository(authRepo) }
    val initialUser = authRepo.getCurrentUser()
    var rolId by remember { mutableStateOf(initialUser?.rolId ?: 0) }
    var userId by remember { mutableStateOf(initialUser?.id ?: 0) }
    var userName by remember { mutableStateOf(initialUser?.fullName ?: "") }
    var userLogin by remember { mutableStateOf(initialUser?.login ?: "") }

    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current

    var route by remember { mutableStateOf<Route>(Route.Home) }
    var refreshKey by remember { mutableStateOf(0) }

    var unreadCount by remember { mutableStateOf(0) }

    // Гарантируем актуальные данные текущего пользователя (id может отсутствовать в старых сессиях)
    LaunchedEffect(Unit) {
        repo.getMe().onSuccess { meResp ->
            val u = meResp.user
            authRepo.updateUserInfo(u.id, u.fio, u.login, u.rolId, u.podrazdelenieId)
            userId = u.id
            userName = u.fio
            userLogin = u.login ?: ""
            rolId = u.rolId ?: rolId
        }
    }

    // Polling непрочитанных сообщений каждые 15 секунд + сразу при возврате на основные вкладки
    LaunchedEffect(route, refreshKey) {
        while (true) {
            repo.getUnreadCount().onSuccess { unreadCount = it.count }
            delay(15_000)
        }
    }

    val isMainTab = route is Route.Home || route is Route.Messages || route is Route.Profile

    Scaffold(
        containerColor = palette.bg,
        bottomBar = {
            if (isMainTab) {
                NavigationBar(containerColor = palette.surface, contentColor = palette.textPrimary) {
                    NavBarItem(s.home, Icons.Default.Home, route is Route.Home, 0) { route = Route.Home }
                    NavBarItem(s.messages, Icons.Default.Mail, route is Route.Messages, unreadCount) {
                        route = Route.Messages
                        // при заходе обнулим визуально (на бэке отметится при открытии конкретного чата)
                    }
                    NavBarItem(s.profile, Icons.Default.Person, route is Route.Profile, 0) { route = Route.Profile }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val r = route) {
                is Route.Home -> HomeScreen(
                    repo = repo,
                    rolId = rolId,
                    onOpenZayavka = { route = Route.ZayavkaDetails(it) },
                    onCreateZayavka = { route = Route.CreateZayavka }
                )
                is Route.Messages -> ConversationsScreen(
                    repo = repo,
                    rolId = rolId,
                    onOpenChat = { uid, fio -> route = Route.Chat(uid, fio) }
                )
                is Route.Profile -> ProfileScreen(
                    fullName = userName,
                    rolId = rolId,
                    login = userLogin,
                    isDark = isDark,
                    language = language,
                    onToggleTheme = onToggleTheme,
                    onChangeLanguage = onChangeLanguage,
                    onLogout = onLogout
                )
                is Route.ZayavkaDetails -> ZayavkaDetailScreen(
                    zayavkaId = r.id,
                    repo = repo,
                    canTake = rolId == 3 || rolId == 4,
                    canEdit = rolId == 5,
                    canDelete = rolId == 5,
                    onBack = { route = Route.Home; refreshKey++ },
                    onChanged = { refreshKey++ }
                )
                is Route.CreateZayavka -> CreateZayavkaScreen(
                    repo = repo,
                    onBack = { route = Route.Home },
                    onCreated = { route = Route.Home; refreshKey++ }
                )
                is Route.Chat -> ChatScreen(
                    repo = repo,
                    otherUserId = r.userId,
                    otherFio = r.fio,
                    currentUserId = userId,
                    canDelete = rolId == 4 || rolId == 5,
                    onBack = { route = Route.Messages }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.NavBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val palette = LocalCarvixPalette.current
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (badgeCount > 0) {
                BadgedBox(badge = {
                    Badge(containerColor = palette.danger, contentColor = androidx.compose.ui.graphics.Color.White) {
                        Text(if (badgeCount > 99) "99+" else "$badgeCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }) { Icon(icon, label) }
            } else {
                Icon(icon, label)
            }
        },
        label = { Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = palette.accent,
            selectedTextColor = palette.accent,
            indicatorColor = palette.card,
            unselectedIconColor = palette.textMuted,
            unselectedTextColor = palette.textMuted
        )
    )
}
