package com.example.carvix.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carvix.data.model.Zayavka
import com.example.carvix.data.repository.AppRepository
import com.example.carvix.ui.*

data class StatusFilter(val statusId: Int?, val labelKey: String)

@Composable
fun ZayavkiTab(
    repo: AppRepository,
    mineOnly: Boolean = false,
    availableOnly: Boolean = false,
    onOpen: (Int) -> Unit,
    extraFilters: List<StatusFilter> = listOf(
        StatusFilter(null, "all"),
        StatusFilter(1, "new"),
        StatusFilter(2, "inProgress"),
        StatusFilter(5, "waitParts"),
        StatusFilter(3, "done"),
        StatusFilter(4, "rejected"),
    )
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val toaster = LocalAppToaster.current
    var selectedFilter by remember { mutableStateOf(extraFilters.first()) }
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<Zayavka>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(selectedFilter, refreshTick) {
        loading = true
        val r = repo.getZayavki(status = selectedFilter.statusId, mine = mineOnly, available = availableOnly)
        loading = false
        r.onSuccess { items = it.zayavki; error = null }
        r.onFailure {
            error = it.message
            toaster.error("Не удалось загрузить заявки: ${it.message}")
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            extraFilters.forEach { f ->
                val selected = f == selectedFilter
                val label = filterLabel(f.labelKey, s)
                FilterChipView(label, selected) { selectedFilter = f }
            }
        }
        if (loading) { LoadingState(); return@Column }
        if (error != null) {
            Column(Modifier.padding(16.dp)) {
                ErrorBanner(error)
                OutlineActionButton(s.retry, { refreshTick++ })
            }
            return@Column
        }
        if (items.isEmpty()) { EmptyState(s.noRequests); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { z -> ZayavkaCard(z, onClick = { onOpen(z.id) }) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun filterLabel(key: String, s: Strings): String = when (key) {
    "all" -> s.all
    "new" -> s.newReq
    "inProgress" -> s.inProgress
    "waitParts" -> s.waitParts
    "done" -> s.done
    "rejected" -> s.rejected
    else -> key
}

@Composable
fun FilterChipView(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalCarvixPalette.current
    val bg = if (selected) palette.accent else palette.surface
    val fg = if (selected) palette.accentOn else palette.textPrimary
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, palette.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ZayavkaCard(z: Zayavka, onClick: () -> Unit) {
    val palette = LocalCarvixPalette.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("#${z.id}  ${z.tipRemontaName ?: ""}", fontWeight = FontWeight.Medium, color = palette.textPrimary, fontSize = 14.sp)
                StatusBadge(statusLabel(z.statusId), statusColor(z.statusId))
            }
            Spacer(Modifier.height(6.dp))
            Text(z.opisanie ?: "—", color = palette.textSecondary, fontSize = 13.sp, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(buildString {
                    append(z.markaName ?: "")
                    if (z.modelName != null) append(" ${z.modelName}")
                    if (z.gosNomer != null) append("  ·  ${z.gosNomer}")
                }.trim(), color = palette.textMuted, fontSize = 12.sp)
                Text(priorityLabel(z.prioritet), color = priorityColor(z.prioritet), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
