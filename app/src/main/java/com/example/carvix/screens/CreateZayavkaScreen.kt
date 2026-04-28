package com.example.carvix.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.carvix.data.model.*
import com.example.carvix.data.repository.AppRepository
import com.example.carvix.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateZayavkaScreen(
    repo: AppRepository,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val s = LocalStrings.current
    val palette = LocalCarvixPalette.current
    val toaster = LocalAppToaster.current
    var refs by remember { mutableStateOf<RefsResponse?>(null) }
    var tsList by remember { mutableStateOf<List<TS>>(emptyList()) }
    var loadingInit by remember { mutableStateOf(true) }
    var initError by remember { mutableStateOf<String?>(null) }

    var selectedTs by remember { mutableStateOf<TS?>(null) }
    var selectedTip by remember { mutableStateOf<RefItem?>(null) }
    var opisanie by remember { mutableStateOf("") }
    var prioritet by remember { mutableStateOf(3) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val r1 = repo.loadRefs()
        val r2 = repo.getTs()
        loadingInit = false
        r1.onSuccess { refs = it }; r1.onFailure { initError = it.message }
        r2.onSuccess { tsList = it.ts }; r2.onFailure { initError = it.message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.createRequest, fontWeight = FontWeight.Medium) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.textPrimary)
            )
        },
        containerColor = palette.bg
    ) { padding ->
        if (loadingInit) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        if (initError != null && refs == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { ErrorBanner(initError) }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DropdownPicker(
                label = s.ts,
                selectedText = selectedTs?.let { "${it.gosNomer} · ${listOfNotNull(it.markaName, it.modelName).joinToString(" ")}" },
                options = tsList.map { it.id to "${it.gosNomer} · ${listOfNotNull(it.markaName, it.modelName).joinToString(" ")}" },
                onSelect = { id -> selectedTs = tsList.first { it.id == id } }
            )
            DropdownPicker(
                label = s.tipRemonta,
                selectedText = selectedTip?.nazvanie,
                options = refs?.tipyRemonta?.map { it.id to it.nazvanie } ?: emptyList(),
                onSelect = { id -> selectedTip = refs?.tipyRemonta?.first { it.id == id } }
            )
            CarvixField(opisanie, { opisanie = it }, s.description, singleLine = false)
            DropdownPicker(
                label = s.priority,
                selectedText = priorityLabel(prioritet),
                options = listOf(1 to s.priorityCritical, 2 to s.priorityHigh, 3 to s.priorityMedium, 4 to s.priorityLow, 5 to s.priorityPlanned),
                onSelect = { prioritet = it }
            )
            ErrorBanner(error)
            PrimaryButton(
                s.createRequest,
                onClick = {
                    if (selectedTs == null || selectedTip == null || opisanie.isBlank()) {
                        error = s.errFillFields
                        toaster.error(s.errFillFields)
                        return@PrimaryButton
                    }
                    saving = true; error = null
                    toaster.action("Создание заявки…")
                    scope.launch {
                        val r = repo.createZayavka(CreateZayavkaRequest(selectedTs!!.id, selectedTip!!.id, opisanie, prioritet))
                        saving = false
                        r.onSuccess {
                            val newId = it.id
                            toaster.success(if (newId != null) "Заявка #$newId создана" else "Заявка создана")
                            onCreated()
                        }
                        r.onFailure {
                            error = it.message
                            toaster.error("Не удалось создать: ${it.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                loading = saving
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownPicker(
    label: String,
    selectedText: String?,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit
) {
    val palette = LocalCarvixPalette.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedText ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = palette.textMuted) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = palette.textMuted) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.textSecondary,
                unfocusedBorderColor = palette.border,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(palette.surface)) {
            options.forEach { (id, label) ->
                DropdownMenuItem(text = { Text(label, color = palette.textPrimary) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
