package com.example.carvix.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carvix.data.model.*
import com.example.carvix.data.repository.AppRepository
import com.example.carvix.ui.*
import kotlinx.coroutines.launch

/**
 * Главный экран сообщений: показывает список диалогов.
 * Для главмеха/админа — также возможность начать новый диалог с механиком (кнопка +)
 * Для механика — список диалогов, плюс быстрый старт чата с любым главмехом
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    repo: AppRepository,
    rolId: Int,
    onOpenChat: (otherUserId: Int, otherFio: String) -> Unit
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val toaster = LocalAppToaster.current
    val canDelete = rolId == 4 || rolId == 5
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var allEmployees by remember { mutableStateOf<List<Sotrudnik>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRecipientPicker by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTick) {
        loading = true
        val cr = repo.getConversations()
        cr.onSuccess { conversations = it.conversations; error = null }
        cr.onFailure {
            error = it.message
            toaster.error("Не удалось загрузить диалоги: ${it.message}")
        }
        // Для возможности начать новый диалог
        val targetRol = when (rolId) {
            3 -> 4 // механик пишет главмеху
            else -> 3 // главмех/админ пишут механикам
        }
        val sr = repo.getSotrudniki(rolId = targetRol)
        sr.onSuccess { allEmployees = it.sotrudniki }
        loading = false
    }

    Scaffold(
        containerColor = palette.bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showRecipientPicker = true },
                containerColor = palette.accent, contentColor = palette.accentOn
            ) { Icon(Icons.Default.Person, "New chat") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ErrorBanner(error)
            if (loading) { LoadingState(); return@Scaffold }
            if (conversations.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(s.noConversations, color = palette.textMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlineActionButton(s.chooseRecipient, { showRecipientPicker = true })
                }
                return@Scaffold
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(conversations, key = { it.id }) { c ->
                    ConversationItem(
                        c = c,
                        onClick = { onOpenChat(c.id, c.fio) },
                        onLongClick = if (canDelete) ({ deleteTarget = c }) else null
                    )
                    HorizontalDivider(color = palette.border, thickness = 0.5.dp)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showRecipientPicker) {
        AlertDialog(
            onDismissRequest = { showRecipientPicker = false },
            title = { Text(s.chooseRecipient, fontWeight = FontWeight.Medium) },
            confirmButton = { TextButton({ showRecipientPicker = false }) { Text(s.cancel, color = palette.textMuted) } },
            text = {
                if (allEmployees.isEmpty()) {
                    Text(s.noEmployees, color = palette.textMuted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(allEmployees, key = { it.id }) { e ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    showRecipientPicker = false
                                    onOpenChat(e.id, e.fio)
                                }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarBubble(e.fio)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(e.fio, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(e.rolName ?: roleLabel(e.rolId), color = palette.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            containerColor = palette.surface
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(s.deleteChat, fontWeight = FontWeight.Medium) },
            text = { Text("${target.fio}\n\n${s.deleteChatConfirm}", color = palette.textSecondary) },
            confirmButton = {
                TextButton({
                    val tId = target.id
                    val tFio = target.fio
                    deleteTarget = null
                    toaster.action("Удаление чата с $tFio…")
                    scope.launch {
                        val r = repo.deleteConversation(tId)
                        r.onSuccess {
                            toaster.success("Чат с $tFio удалён")
                            refreshTick++
                        }
                        r.onFailure {
                            error = it.message
                            toaster.error("Ошибка: ${it.message}")
                        }
                    }
                }) { Text(s.deleteBtn, color = palette.danger, fontWeight = FontWeight.Medium) }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text(s.cancel, color = palette.textMuted) } },
            containerColor = palette.surface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(c: Conversation, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val palette = LocalCarvixPalette.current
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarBubble(c.fio)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c.fio, fontWeight = FontWeight.Medium, color = palette.textPrimary, fontSize = 14.sp)
                Text(c.lastTime?.replace("T", " ")?.take(16) ?: "", fontSize = 10.sp, color = palette.textMuted)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(c.lastMessage ?: "—", color = palette.textSecondary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                if ((c.unread ?: 0) > 0) {
                    Box(
                        Modifier.padding(start = 8.dp).clip(CircleShape).background(palette.accent).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${c.unread}", color = palette.accentOn, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarBubble(name: String) {
    val palette = LocalCarvixPalette.current
    val initials = name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(palette.card),
        contentAlignment = Alignment.Center
    ) {
        Text(initials.ifBlank { "?" }, color = palette.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repo: AppRepository,
    otherUserId: Int,
    otherFio: String,
    currentUserId: Int,
    canDelete: Boolean = false,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val toaster = LocalAppToaster.current
    var messages by remember { mutableStateOf<List<FeedbackMsg>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(refreshTick) {
        loading = true
        val r = repo.getMessagesWith(otherUserId)
        loading = false
        r.onSuccess { messages = it.messages; error = null }
        r.onFailure {
            error = it.message
            toaster.error("Не удалось загрузить сообщения: ${it.message}")
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBubble(otherFio)
                        Spacer(Modifier.width(10.dp))
                        Text(otherFio, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.textPrimary) } },
                actions = {
                    if (canDelete) {
                        IconButton({ showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, s.deleteChat, tint = palette.danger)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.textPrimary)
            )
        },
        containerColor = palette.bg,
        bottomBar = {
            Surface(color = palette.surface, tonalElevation = 4.dp) {
                Row(Modifier.padding(8.dp).navigationBarsPadding(), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(s.messagePlaceholder, color = palette.textMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.border,
                            unfocusedBorderColor = palette.border,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedContainerColor = palette.surface,
                            unfocusedContainerColor = palette.surface
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isBlank()) return@IconButton
                            sending = true
                            val text = input.trim()
                            scope.launch {
                                val r = repo.sendFeedback(FeedbackRequest(text, komuId = otherUserId))
                                sending = false
                                r.onSuccess {
                                    toaster.success("Сообщение отправлено")
                                    input = ""; refreshTick++
                                }
                                r.onFailure {
                                    error = it.message
                                    toaster.error("Не удалось отправить: ${it.message}")
                                }
                            }
                        },
                        enabled = !sending && input.isNotBlank()
                    ) {
                        if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.accent)
                        else Icon(Icons.AutoMirrored.Filled.Send, s.sendMessage, tint = palette.accent)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ErrorBanner(error)
            if (loading) { LoadingState(); return@Scaffold }
            if (messages.isEmpty()) {
                EmptyState(s.noMessages)
                return@Scaffold
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { m ->
                    val isMine = m.otSotrudnikaId == currentUserId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier
                                .fillMaxWidth(0.78f)
                                .clip(RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp,
                                    bottomStart = if (isMine) 16.dp else 4.dp,
                                    bottomEnd = if (isMine) 4.dp else 16.dp
                                ))
                                .background(if (isMine) palette.accent else palette.card)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(m.soobshenie, color = if (isMine) palette.accentOn else palette.textPrimary, fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                m.dataSozdaniya?.replace("T", " ")?.take(16) ?: "",
                                fontSize = 10.sp,
                                color = if (isMine) palette.accentOn.copy(alpha = 0.7f) else palette.textMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(s.deleteChat, fontWeight = FontWeight.Medium) },
            text = { Text("$otherFio\n\n${s.deleteChatConfirm}", color = palette.textSecondary) },
            confirmButton = {
                TextButton({
                    showDeleteDialog = false
                    toaster.action("Удаление чата с $otherFio…")
                    scope.launch {
                        val r = repo.deleteConversation(otherUserId)
                        r.onSuccess {
                            toaster.success("Чат с $otherFio удалён")
                            onBack()
                        }
                        r.onFailure {
                            error = it.message
                            toaster.error("Ошибка: ${it.message}")
                        }
                    }
                }) { Text(s.deleteBtn, color = palette.danger, fontWeight = FontWeight.Medium) }
            },
            dismissButton = { TextButton({ showDeleteDialog = false }) { Text(s.cancel, color = palette.textMuted) } },
            containerColor = palette.surface
        )
    }
}
