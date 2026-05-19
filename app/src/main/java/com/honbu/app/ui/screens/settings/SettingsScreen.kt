package com.honbu.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honbu.app.data.preferences.SoundType
import com.honbu.app.ui.theme.KendoGold
import com.honbu.app.ui.theme.KendoRed
import com.honbu.app.viewmodel.SettingsViewModel
import com.honbu.app.viewmodel.SoundSlot

private val ALL_SOUNDS = SoundType.entries.toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val cfg          by vm.soundConfig.collectAsState()
    val members      by vm.members.collectAsState()
    val importResult by vm.importResult.collectAsState()

    var newName by remember { mutableStateOf("") }

    val timerEndPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> vm.setCustomUri(SoundSlot.TIMER_END, uri) }
    }
    val changePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> vm.setCustomUri(SoundSlot.INTERVAL_CHANGE, uri) }
    }
    val startPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> vm.setCustomUri(SoundSlot.INTERVAL_START, uri) }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> vm.importMembersFromCsv(uri) }
    }
    // "Save As" — create a new CSV at any location with any filename
    val csvCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { it?.let { uri -> vm.createNewCsvFile(uri) } }
    // "Open existing" — pick an existing CSV to append to
    val csvOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { it?.let { uri -> vm.useExistingCsvFile(uri) } }

    importResult?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearImportResult,
            title = { Text(if (msg.startsWith("Import failed")) "Error" else "Import complete") },
            text  = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearImportResult) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Sound ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Text("Sound Effects", style = MaterialTheme.typography.titleLarge, color = KendoGold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap ▶ to preview. Choose CUSTOM to use your own audio file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SoundPickerCard("Timer End  (down timer & match)", cfg.timerEndSound,
                    { vm.setTimerEndSound(it); if (it == SoundType.CUSTOM) timerEndPicker.launch(arrayOf("audio/*")) },
                    { timerEndPicker.launch(arrayOf("audio/*")) },
                    { vm.previewSound(SoundSlot.TIMER_END) })
            }
            item {
                SoundPickerCard("Interval Change  (work → switch)", cfg.intervalChangeSound,
                    { vm.setIntervalChangeSound(it); if (it == SoundType.CUSTOM) changePicker.launch(arrayOf("audio/*")) },
                    { changePicker.launch(arrayOf("audio/*")) },
                    { vm.previewSound(SoundSlot.INTERVAL_CHANGE) })
            }
            item {
                SoundPickerCard("Interval Start  (switch → work)", cfg.intervalStartSound,
                    { vm.setIntervalStartSound(it); if (it == SoundType.CUSTOM) startPicker.launch(arrayOf("audio/*")) },
                    { startPicker.launch(arrayOf("audio/*")) },
                    { vm.previewSound(SoundSlot.INTERVAL_START) })
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── CSV save file ──────────────────────────────────
            item {
                Text("CSV Save File", style = MaterialTheme.typography.titleLarge, color = KendoGold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Results are appended to one CSV file. Use \"Save as\" to pick a " +
                    "location and filename, or \"Use existing\" to append to a file you " +
                    "already have. The default is Downloads/honbu_matches.csv.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                val fileUri = cfg.csvFileUri
                // Try to resolve a human-readable name from the URI
                val context = LocalContext.current
                val displayName = remember(fileUri) {
                    if (fileUri.isEmpty()) return@remember null
                    try {
                        context.contentResolver.query(
                            android.net.Uri.parse(fileUri),
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null, null, null
                        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } catch (_: Exception) { null }
                }

                Card(
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                        // Current file indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (fileUri.isEmpty()) Icons.Default.FolderOpen else Icons.Default.InsertDriveFile,
                                null, tint = KendoGold, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (fileUri.isEmpty()) "Default — Downloads/honbu_matches.csv"
                                else displayName ?: "Custom file",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Save As / Use existing buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick  = { csvCreateLauncher.launch("honbu_matches.csv") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save as…", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick  = { csvOpenLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Use existing…", maxLines = 1)
                            }
                        }

                        // Reset to default (only when custom is set)
                        if (fileUri.isNotEmpty()) {
                            OutlinedButton(
                                onClick  = vm::clearCustomCsvFile,
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = KendoRed),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Reset to default") }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── Members ────────────────────────────────────────
            item {
                Text("Members", style = MaterialTheme.typography.titleLarge, color = KendoGold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "These names appear in Match Timer and Pools dropdowns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("New member name") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick  = { vm.addMember(newName); newName = "" },
                        enabled  = newName.isNotBlank(),
                        colors   = IconButtonDefaults.filledIconButtonColors(containerColor = KendoGold)
                    ) { Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.background) }
                }
            }
            item {
                OutlinedButton(
                    onClick  = { csvPicker.launch(arrayOf("text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import members from text/CSV file")
                }
                Text(
                    "One name per line. UTF-8 and Windows-1250 (CP1250) supported. " +
                    "Duplicates are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (members.isEmpty()) {
                item {
                    Text("No members yet.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            items(members, key = { it.id }) { member ->
                Card(
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(member.name, style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { vm.deleteMember(member) }) {
                            Icon(Icons.Default.Delete, "Delete ${member.name}", tint = KendoRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundPickerCard(
    label: String,
    selected: SoundType,
    onSelect: (SoundType) -> Unit,
    onPickFile: () -> Unit,
    onPreview: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected.name)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ALL_SOUNDS.forEach { type ->
                            DropdownMenuItem(
                                text    = { Text(type.name) },
                                onClick = { onSelect(type); expanded = false }
                            )
                        }
                    }
                }
                IconButton(onClick = onPreview) {
                    Icon(Icons.Default.PlayArrow, "Preview", tint = KendoGold)
                }
            }
            if (selected == SoundType.CUSTOM) {
                TextButton(onClick = onPickFile) { Text("Pick audio file…", color = KendoGold) }
            }
        }
    }
}
