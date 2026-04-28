package com.example.carvix.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carvix.data.model.*
import com.example.carvix.data.repository.AppRepository
import com.example.carvix.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZayavkaDetailScreen(
    zayavkaId: Int,
    repo: AppRepository,
    canTake: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val toaster = LocalAppToaster.current
    var data by remember { mutableStateOf<ZayavkaDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionInProgress by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(zayavkaId, refreshTick) {
        loading = true
        val r = repo.getZayavka(zayavkaId)
        loading = false
        r.onSuccess { data = it; error = null }
        r.onFailure {
            error = it.message
            toaster.error("Не удалось загрузить заявку: ${it.message}")
        }
    }

    var showCompleteDialog by remember { mutableStateOf(false) }
    var statusToSet by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${s.request} #$zayavkaId", fontWeight = FontWeight.Medium) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.textPrimary)
            )
        },
        containerColor = palette.bg
    ) { padding ->
        if (loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        if (error != null || data == null) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                ErrorBanner(error ?: s.errLoadFailed)
                OutlineActionButton(s.retry, { refreshTick++ })
            }
            return@Scaffold
        }
        val z = data!!.zayavka
        val rem = data!!.remont
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(z.tipRemontaName ?: s.request, fontWeight = FontWeight.Medium, color = palette.textPrimary, fontSize = 16.sp)
                    StatusBadge(statusLabel(z.statusId), statusColor(z.statusId))
                }
                Spacer(Modifier.height(8.dp))
                Text(z.opisanie ?: "—", color = palette.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                LabelValueRow(s.priority, priorityLabel(z.prioritet))
                LabelValueRow(s.creator, z.sozdatelFio)
                LabelValueRow(s.createdAt, z.dataSozdaniya?.take(10))
            }
            SectionCard {
                Text(s.ts, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                Spacer(Modifier.height(8.dp))
                LabelValueRow(s.markaModel, listOfNotNull(z.markaName, z.modelName).joinToString(" "))
                LabelValueRow(s.gosNomer, z.gosNomer)
                LabelValueRow(s.inventNomer, z.inventNomer)
                LabelValueRow(s.mileage, z.probeg?.let { "$it" })
                LabelValueRow(s.state, z.tekuscheeSostoyanie)
            }
            if (rem != null) {
                SectionCard {
                    Text(s.repair, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    LabelValueRow(s.mechanicLabel, rem.mekhanikFio)
                    LabelValueRow(s.headMechLabel, rem.glavniyMekhanikFio)
                    LabelValueRow(s.started, rem.dataNachala?.take(10))
                    LabelValueRow(s.finished, rem.dataOkonchaniya?.take(10))
                    LabelValueRow(s.laborCost, rem.stoimostRabot)
                    LabelValueRow(s.partsCost, rem.stoimostZapchastey)
                    LabelValueRow(s.comment, rem.kommentariy)
                    LabelValueRow(s.result, rem.itog)
                }
            }
            if (canTake && z.statusId == 1) {
                PrimaryButton(
                    text = s.takeInWork,
                    onClick = {
                        actionInProgress = true
                        toaster.action("Берём заявку #$zayavkaId…")
                        scope.launch {
                            val r = repo.takeZayavka(zayavkaId)
                            actionInProgress = false
                            r.onSuccess {
                                toaster.success("Заявка #$zayavkaId взята в работу")
                                refreshTick++; onChanged()
                            }
                            r.onFailure {
                                error = it.message
                                toaster.error("Не удалось взять: ${it.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = actionInProgress
                )
            }
            if (z.statusId == 2 && rem != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineActionButton(s.statusWaitParts, { statusToSet = 5; showCompleteDialog = true }, Modifier.weight(1f))
                    PrimaryButton(s.complete, { statusToSet = 3; showCompleteDialog = true }, Modifier.weight(1f))
                }
                OutlineActionButton(s.reject, { statusToSet = 4; showCompleteDialog = true }, Modifier.fillMaxWidth())
            }
            if (z.statusId == 5 && rem != null) {
                PrimaryButton(s.complete, {
                    statusToSet = 3; showCompleteDialog = true
                }, Modifier.fillMaxWidth())
            }
            if (canDelete) {
                OutlineActionButton(s.deleteReq, {
                    actionInProgress = true
                    toaster.action("Удаление заявки #$zayavkaId…")
                    scope.launch {
                        val r = repo.deleteZayavka(zayavkaId)
                        actionInProgress = false
                        r.onSuccess {
                            toaster.success("Заявка #$zayavkaId удалена")
                            onChanged(); onBack()
                        }
                        r.onFailure {
                            error = it.message
                            toaster.error("Ошибка удаления: ${it.message}")
                        }
                    }
                }, Modifier.fillMaxWidth())
            }
        }
    }

    if (showCompleteDialog && statusToSet != null) {
        CompleteDialog(
            statusId = statusToSet!!,
            onDismiss = { showCompleteDialog = false; statusToSet = null },
            onConfirm = { kommentariy, itog, sr, sz ->
                showCompleteDialog = false
                actionInProgress = true
                val newStatus = statusToSet!!
                val statusName = when (newStatus) {
                    3 -> s.statusDone
                    4 -> s.statusRejected
                    5 -> s.statusWaitParts
                    else -> "#$newStatus"
                }
                toaster.action("Смена статуса заявки #$zayavkaId → $statusName…")
                scope.launch {
                    val r = repo.changeStatus(zayavkaId, StatusChangeRequest(newStatus, kommentariy, itog, sr, sz))
                    actionInProgress = false
                    r.onSuccess {
                        toaster.success("Статус заявки #$zayavkaId: $statusName")
                        refreshTick++; onChanged()
                    }
                    r.onFailure {
                        error = it.message
                        toaster.error("Ошибка смены статуса: ${it.message}")
                    }
                    statusToSet = null
                }
            }
        )
    }
}

@Composable
private fun CompleteDialog(
    statusId: Int,
    onDismiss: () -> Unit,
    onConfirm: (kommentariy: String?, itog: String?, sr: Double?, sz: Double?) -> Unit
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    var kommentariy by remember { mutableStateOf("") }
    var itog by remember { mutableStateOf("") }
    var stoimostRabot by remember { mutableStateOf("") }
    var stoimostZap by remember { mutableStateOf("") }
    val title = when (statusId) {
        3 -> s.complete
        4 -> s.reject
        5 -> s.statusWaitParts
        else -> s.confirm
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                onConfirm(
                    kommentariy.takeIf { it.isNotBlank() },
                    itog.takeIf { it.isNotBlank() },
                    stoimostRabot.toDoubleOrNull(),
                    stoimostZap.toDoubleOrNull()
                )
            }) { Text(s.confirm, color = palette.textPrimary) }
        },
        dismissButton = { TextButton(onDismiss) { Text(s.cancel, color = palette.textMuted) } },
        title = { Text(title, fontWeight = FontWeight.Medium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CarvixField(kommentariy, { kommentariy = it }, s.comment, singleLine = false)
                if (statusId == 3) {
                    CarvixField(itog, { itog = it }, s.result)
                    CarvixField(stoimostRabot, { stoimostRabot = it.filter { c -> c.isDigit() || c == '.' } }, s.laborCost, keyboardType = KeyboardType.Decimal)
                    CarvixField(stoimostZap, { stoimostZap = it.filter { c -> c.isDigit() || c == '.' } }, s.partsCost, keyboardType = KeyboardType.Decimal)
                }
            }
        },
        containerColor = palette.surface
    )
}
