package com.honbu.app.ui.screens.pools

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honbu.app.ui.theme.KendoGold
import com.honbu.app.ui.theme.KendoRed
import com.honbu.app.ui.theme.KendoSurfaceVariant
import com.honbu.app.viewmodel.RANDOM_PICK
import com.honbu.app.viewmodel.PoolsViewModel
import com.honbu.app.viewmodel.ScheduleType

private val DURATION_PRESETS = listOf(
    "30s" to 30_000L, "1m" to 60_000L, "1:30" to 90_000L,
    "2m"  to 2*60_000L, "3m" to 3*60_000L, "4m" to 4*60_000L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PoolsSetupScreen(vm: PoolsViewModel, onBack: () -> Unit) {
    val members          by vm.members.collectAsState()
    val selectedMembers  by vm.selectedMembers.collectAsState()
    val scheduleType     by vm.scheduleType.collectAsState()
    val clusterSize      by vm.clusterSize.collectAsState()
    val firstWhite       by vm.firstWhite.collectAsState()
    val firstRed         by vm.firstRed.collectAsState()
    val poolDuration     by vm.poolDurationMs.collectAsState()

    var showScheduleMenu by remember { mutableStateOf(false) }
    var showClusterMenu  by remember { mutableStateOf(false) }
    var showWhiteMenu    by remember { mutableStateOf(false) }
    var showRedMenu      by remember { mutableStateOf(false) }

    // Options for first-match pickers (filter out the other side's selection)
    val whiteOptions = listOf(RANDOM_PICK) + selectedMembers.filter {
        firstRed == RANDOM_PICK || it != firstRed }
    val redOptions = listOf(RANDOM_PICK) + selectedMembers.filter {
        firstWhite == RANDOM_PICK || it != firstWhite }

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("New Pool") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )}
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Match duration ────────────────────────────────────
            Text("Match Duration", style = MaterialTheme.typography.titleMedium, color = KendoGold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DURATION_PRESETS.forEach { (label, ms) ->
                    FilterChip(
                        selected = poolDuration == ms,
                        onClick  = { vm.setPoolDuration(ms) },
                        label    = { Text(label) }
                    )
                }
            }

            HorizontalDivider()

            // ── Member selection ──────────────────────────────────
            Text("Participants  (${selectedMembers.size} selected)",
                style = MaterialTheme.typography.titleMedium, color = KendoGold)
            if (members.isEmpty()) {
                Text("No members found — add them in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { member ->
                        val isSelected = member.name in selectedMembers
                        FilterChip(
                            selected = isSelected,
                            onClick  = { vm.toggleMember(member.name) },
                            label    = { Text(member.name) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Schedule type ─────────────────────────────────────
            Text("Schedule Type", style = MaterialTheme.typography.titleMedium, color = KendoGold)
            Box {
                OutlinedButton(onClick = { showScheduleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(when (scheduleType) {
                        ScheduleType.CIRCLE         -> "Circle method"
                        ScheduleType.REST_OPTIMIZED -> "Rest-optimized"
                        ScheduleType.CLUSTERED      -> "Clustered"
                        ScheduleType.RANDOM         -> "Random"
                        ScheduleType.RANDOM_REST    -> "Random with rest"
                    })
                }
                DropdownMenu(expanded = showScheduleMenu, onDismissRequest = { showScheduleMenu = false }) {
                    listOf(
                        ScheduleType.CIRCLE         to "Circle method",
                        ScheduleType.REST_OPTIMIZED to "Rest-optimized",
                        ScheduleType.CLUSTERED      to "Clustered",
                        ScheduleType.RANDOM         to "Random",
                        ScheduleType.RANDOM_REST    to "Random with rest",
                    ).forEach { (type, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            vm.setScheduleType(type); showScheduleMenu = false })
                    }
                }
            }

            // Cluster size selector — only shown when Clustered is selected
            if (scheduleType == ScheduleType.CLUSTERED) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Cluster size:", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Box {
                        OutlinedButton(onClick = { showClusterMenu = true }) {
                            Text("$clusterSize matches")
                        }
                        DropdownMenu(expanded = showClusterMenu, onDismissRequest = { showClusterMenu = false }) {
                            (2..5).forEach { n ->
                                DropdownMenuItem(text = { Text("$n matches") }, onClick = {
                                    vm.setClusterSize(n); showClusterMenu = false })
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── First match pickers ───────────────────────────────
            Text("First Match", style = MaterialTheme.typography.titleMedium, color = KendoGold)
            if (selectedMembers.size >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // White side
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("White (left)", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            OutlinedButton(onClick = { showWhiteMenu = true }, modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, com.honbu.app.ui.theme.WhitePlayerColor)) {
                                Text(firstWhite, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }
                            DropdownMenu(expanded = showWhiteMenu, onDismissRequest = { showWhiteMenu = false }) {
                                whiteOptions.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = {
                                        vm.setFirstWhite(opt); showWhiteMenu = false })
                                }
                            }
                        }
                    }
                    // Red side
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Red (right)", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            OutlinedButton(onClick = { showRedMenu = true }, modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, com.honbu.app.ui.theme.RedPlayerColor)) {
                                Text(firstRed, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }
                            DropdownMenu(expanded = showRedMenu, onDismissRequest = { showRedMenu = false }) {
                                redOptions.forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = {
                                        vm.setFirstRed(opt); showRedMenu = false })
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Select at least 2 participants to configure first match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))

            // ── Generate button ───────────────────────────────────
            Button(
                onClick  = vm::generateSchedule,
                enabled  = selectedMembers.size >= 2,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = KendoGold,
                    contentColor = com.honbu.app.ui.theme.KendoDark)
            ) {
                Text("Generate Schedule", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
