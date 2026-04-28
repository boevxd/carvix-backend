package com.example.carvix.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carvix.data.model.MekhanikActive
import com.example.carvix.data.model.Sotrudnik
import com.example.carvix.data.model.TS
import com.example.carvix.data.model.RefsResponse
import com.example.carvix.data.model.CreateSotrudnikRequest
import com.example.carvix.data.model.UpdateSotrudnikRequest
import com.example.carvix.data.repository.AppRepository
import com.example.carvix.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: AppRepository,
    rolId: Int,
    onOpenZayavka: (Int) -> Unit,
    onCreateZayavka: () -> Unit
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current

    val tabs = remember(rolId, s) {
        when (rolId) {
            4 -> listOf(s.zayavki, s.mechanics, s.transport)
            5 -> listOf(s.zayavki, s.sotrudniki, s.transport)
            else -> listOf(s.zayavki) // механик: только заявки
        }
    }
    var tab by remember { mutableStateOf(0) }
    val showCreateFab = (rolId == 4 || rolId == 5) && tab == 0

    Scaffold(
        containerColor = palette.bg,
        floatingActionButton = {
            if (showCreateFab) {
                FloatingActionButton(onClick = onCreateZayavka, containerColor = palette.accent, contentColor = palette.accentOn) {
                    Icon(Icons.Default.Add, s.createRequest)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (tabs.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    containerColor = palette.bg,
                    contentColor = palette.textPrimary,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { i, name ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name, fontSize = 13.sp) })
                    }
                }
            }
            when (rolId) {
                3 -> {
                    // Механик видит только свободные новые + те, что взял сам
                    ZayavkiTab(
                        repo = repo,
                        availableOnly = true,
                        onOpen = onOpenZayavka,
                        extraFilters = listOf(
                            StatusFilter(null, "all"),
                            StatusFilter(1, "new"),
                            StatusFilter(2, "inProgress"),
                            StatusFilter(5, "waitParts"),
                            StatusFilter(3, "done"),
                        )
                    )
                }
                4 -> when (tab) {
                    0 -> ZayavkiTab(repo, onOpen = onOpenZayavka)
                    1 -> MechanicsListSection(repo, onOpenZayavka)
                    2 -> TsListSection(repo)
                }
                5 -> when (tab) {
                    0 -> ZayavkiTab(repo, onOpen = onOpenZayavka)
                    1 -> SotrudnikiSection(repo)
                    2 -> TsListSection(repo)
                }
                else -> ZayavkiTab(repo, onOpen = onOpenZayavka)
            }
        }
    }
}

@Composable
fun MechanicsListSection(repo: AppRepository, onOpenZayavka: (Int) -> Unit) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val toaster = LocalAppToaster.current
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<MekhanikActive>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val r = repo.getActiveMechanics()
        loading = false
        r.onSuccess { items = it.mekhaniki; error = null }
        r.onFailure {
            error = it.message
            toaster.error("Не удалось загрузить механиков: ${it.message}")
        }
    }
    if (loading) { LoadingState(); return }
    if (error != null) { ErrorBanner(error); return }
    if (items.isEmpty()) { EmptyState(s.noEmployees); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { m ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = palette.surface), border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.fio, fontWeight = FontWeight.Medium, color = palette.textPrimary, fontSize = 14.sp)
                            if (!m.login.isNullOrBlank()) Text("@${m.login}", fontSize = 11.sp, color = palette.textMuted)
                        }
                        StatusBadge("${s.activeRepairs}: ${m.activeRemonts}", if (m.activeRemonts > 0) palette.warning else palette.success)
                    }
                    if (!m.activeZayavki.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        m.activeZayavki.forEach { z ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenZayavka(z.zayavkaId) }.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("#${z.zayavkaId}: ${z.opisanie ?: "—"}", fontSize = 12.sp, color = palette.textSecondary, modifier = Modifier.weight(1f))
                                Text(z.gosNomer ?: "", fontSize = 12.sp, color = palette.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TsListSection(repo: AppRepository) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<TS>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val toaster = LocalAppToaster.current
    LaunchedEffect(Unit) {
        val r = repo.getTs()
        loading = false
        r.onSuccess { items = it.ts; error = null }
        r.onFailure {
            error = it.message
            toaster.error("Не удалось загрузить ТС: ${it.message}")
        }
    }
    if (loading) { LoadingState(); return }
    if (error != null) { ErrorBanner(error); return }
    if (items.isEmpty()) { EmptyState(s.noVehicles); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { ts ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = palette.surface), border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(ts.markaName, ts.modelName).joinToString(" ").ifBlank { "ТС #${ts.id}" }, fontWeight = FontWeight.Medium, color = palette.textPrimary, fontSize = 14.sp)
                        StatusBadge(ts.tekuscheeSostoyanie ?: "—", tsStateColor(ts.tekuscheeSostoyanie))
                    }
                    Spacer(Modifier.height(6.dp))
                    LabelValueRow(s.gosNomer, ts.gosNomer)
                    LabelValueRow(s.inventNomer, ts.inventNomer)
                    LabelValueRow(s.mileage, ts.probeg?.let { "$it" })
                    LabelValueRow(s.division, ts.podrazdelenieName)
                }
            }
        }
    }
}

@Composable
fun SotrudnikiSection(repo: AppRepository) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val toaster = LocalAppToaster.current
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<Sotrudnik>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refs by remember { mutableStateOf<RefsResponse?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var editTarget by remember { mutableStateOf<Sotrudnik?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshTick) {
        loading = true
        val r = repo.getSotrudniki()
        loading = false
        r.onSuccess { items = it.sotrudniki; error = null }
        r.onFailure {
            error = it.message
            toaster.error("Не удалось загрузить сотрудников: ${it.message}")
        }
        if (refs == null) {
            repo.loadRefs().onSuccess { refs = it }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ErrorBanner(error)
            if (loading) { LoadingState(); return@Column }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { sItem ->
                    Card(
                        Modifier.fillMaxWidth().clickable { editTarget = sItem },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sItem.fio, fontWeight = FontWeight.Medium, color = palette.textPrimary, fontSize = 14.sp)
                                Text("@${sItem.login ?: "—"}", fontSize = 11.sp, color = palette.textMuted)
                            }
                            StatusBadge(sItem.rolName ?: roleLabel(sItem.rolId), palette.info)
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = palette.accent, contentColor = palette.accentOn
        ) { Icon(Icons.Default.Add, "Add") }
    }

    if (showCreate) {
        SotrudnikDialog(
            initial = null,
            refs = refs,
            onDismiss = { showCreate = false },
            onSave = { req ->
                toaster.action("Создание сотрудника ${req.fio}…")
                scope.launch {
                    val r = repo.createSotrudnik(req)
                    r.onSuccess {
                        toaster.success("Сотрудник ${req.fio} создан")
                        showCreate = false; refreshTick++
                    }
                    r.onFailure {
                        error = it.message
                        toaster.error("Ошибка создания: ${it.message}")
                    }
                }
            },
            onDelete = null
        )
    }
    if (editTarget != null) {
        SotrudnikDialog(
            initial = editTarget,
            refs = refs,
            onDismiss = { editTarget = null },
            onSave = { req ->
                val targetFio = editTarget?.fio ?: req.fio
                toaster.action("Обновление $targetFio…")
                scope.launch {
                    val update = UpdateSotrudnikRequest(
                        fio = req.fio,
                        login = req.login,
                        password = req.password.takeIf { it.isNotBlank() },
                        rolId = req.rolId,
                        podrazdelenieId = req.podrazdelenieId
                    )
                    val r = repo.updateSotrudnik(editTarget!!.id, update)
                    r.onSuccess {
                        toaster.success("Сотрудник ${req.fio} обновлён")
                        editTarget = null; refreshTick++
                    }
                    r.onFailure {
                        error = it.message
                        toaster.error("Ошибка обновления: ${it.message}")
                    }
                }
            },
            onDelete = {
                val targetFio = editTarget?.fio ?: "сотрудника"
                toaster.action("Удаление $targetFio…")
                scope.launch {
                    val r = repo.deleteSotrudnik(editTarget!!.id)
                    r.onSuccess {
                        toaster.success("$targetFio удалён")
                        editTarget = null; refreshTick++
                    }
                    r.onFailure {
                        error = it.message
                        toaster.error("Ошибка удаления: ${it.message}")
                    }
                }
            }
        )
    }
}

@Composable
fun SotrudnikDialog(
    initial: Sotrudnik?,
    refs: RefsResponse?,
    onDismiss: () -> Unit,
    onSave: (CreateSotrudnikRequest) -> Unit,
    onDelete: (() -> Unit)?
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    var fio by remember { mutableStateOf(initial?.fio ?: "") }
    var login by remember { mutableStateOf(initial?.login ?: "") }
    var password by remember { mutableStateOf("") }
    var rolId by remember { mutableStateOf(initial?.rolId ?: 3) }
    var podrId by remember { mutableStateOf(initial?.podrazdelenieId ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                if (fio.isBlank() || login.isBlank() || (initial == null && password.isBlank())) return@TextButton
                onSave(CreateSotrudnikRequest(fio, login, password, rolId, podrId))
            }) { Text(s.saveBtn, color = palette.textPrimary) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onDelete) { Text(s.deleteBtn, color = palette.danger) }
                }
                TextButton(onDismiss) { Text(s.cancel, color = palette.textMuted) }
            }
        },
        title = { Text(if (initial == null) s.newEmployee else s.edit, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CarvixField(fio, { fio = it }, s.fio)
                CarvixField(login, { login = it }, s.login)
                CarvixField(password, { password = it }, if (initial == null) s.password else s.newPassword)
                DropdownPicker(
                    label = s.role,
                    selectedText = roleLabel(rolId),
                    options = (refs?.roles ?: emptyList()).map { it.id to it.nazvanie },
                    onSelect = { rolId = it }
                )
                DropdownPicker(
                    label = s.division,
                    selectedText = refs?.podrazdeleniya?.firstOrNull { it.id == podrId }?.nazvanie ?: "—",
                    options = (refs?.podrazdeleniya ?: emptyList()).map { it.id to it.nazvanie },
                    onSelect = { podrId = it }
                )
            }
        },
        containerColor = palette.surface
    )
}

@Composable
private fun tsStateColor(state: String?): Color {
    val palette = LocalCarvixPalette.current
    return when (state?.lowercase()) {
        "в работе" -> palette.success
        "на то" -> palette.warning
        "в ремонте" -> palette.statusInProgress
        "списано" -> palette.danger
        else -> palette.textMuted
    }
}
