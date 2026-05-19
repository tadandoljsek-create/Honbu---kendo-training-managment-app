package com.honbu.app.ui.screens.match

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honbu.app.ui.components.*
import com.honbu.app.ui.theme.*
import com.honbu.app.util.TimeFormatter
import com.honbu.app.viewmodel.*

private val PRESETS = listOf(
    "30s" to 30_000L, "1m" to 60_000L, "1:30" to 90_000L,
    "2m"  to 2*60_000L, "3m" to 3*60_000L, "4m" to 4*60_000L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MatchTimerScreen(onBack: () -> Unit) {
    val vm: MatchTimerViewModel = viewModel()
    val state        by vm.state.collectAsState()
    val members      by vm.members.collectAsState()
    val submitResult by vm.submitResult.collectAsState()
    val context = LocalContext.current

    var showResetDialog  by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var showBackWarning  by remember { mutableStateOf(false) }
    var customMins by remember { mutableStateOf("3") }
    var customSecs by remember { mutableStateOf("00") }
    val memberNames = remember(members) { members.map { it.name } }
    val scoringEnabled = state.phase in MatchState.SCORING_PHASES

    // Keep screen on while match is active
    val view = LocalView.current
    val active = state.phase != MatchPhase.IDLE && state.phase != MatchPhase.FINISHED
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    // Back intercept: warn if match started but not submitted
    val needsWarning = state.phase != MatchPhase.IDLE && !state.hasSubmittedResults
    BackHandler(enabled = needsWarning) { showBackWarning = true }

    val writePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted -> if (granted) vm.submit(context)
    }
    fun doSubmit() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else vm.submit(context)
    }

    // ── Dialogs ──────────────────────────────────────────────────────

    if (showBackWarning) {
        AlertDialog(
            onDismissRequest = { showBackWarning = false },
            title = { Text("Leave without submitting?") },
            text  = { Text("The match has been started but results haven't been submitted yet.") },
            confirmButton = { TextButton(onClick = { showBackWarning = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { showBackWarning = false }) { Text("Stay") } }
        )
    }

    submitResult?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearSubmitResult,
            title = { Text(if (msg.startsWith("Error")) "Error" else "✓ Saved") },
            text  = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearSubmitResult) { Text("OK") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset match?") },
            text  = { Text("All recorded points and notes will be cleared.") },
            confirmButton = { TextButton(
                onClick = { vm.resetMatch(); showResetDialog = false },
                colors  = ButtonDefaults.textButtonColors(contentColor = KendoRed)
            ) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom duration") },
            text  = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = customMins, onValueChange = { customMins = it },
                        label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f))
                    Text(":")
                    OutlinedTextField(value = customSecs, onValueChange = { customSecs = it },
                        label = { Text("Sec") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            },
            confirmButton = { TextButton(onClick = {
                val ms = ((customMins.toIntOrNull() ?: 0) * 60L + (customSecs.toIntOrNull() ?: 0)) * 1_000L
                if (ms > 0) vm.setDuration(ms)
                showCustomDialog = false
            }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Match Timer") },
            navigationIcon = { IconButton(onClick = { if (needsWarning) showBackWarning = true else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )}
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Duration presets
            val canChange = state.phase == MatchPhase.IDLE
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEach { (label, ms) ->
                    FilterChip(selected = state.durationMs == ms && canChange, enabled = canChange,
                        onClick = { vm.setDuration(ms) }, label = { Text(label, fontSize = 13.sp) })
                }
                FilterChip(selected = false, enabled = canChange, onClick = { showCustomDialog = true },
                    label = { Text("custom", fontSize = 13.sp) })
            }

            // Player dropdowns
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerDropdown(state.whitePlayer, memberNames, { vm.updatePlayerName(PlayerSide.WHITE, it) },
                    WhitePlayerColor, Modifier.weight(1f))
                PlayerDropdown(state.redPlayer, memberNames, { vm.updatePlayerName(PlayerSide.RED, it) },
                    RedPlayerColor, Modifier.weight(1f))
            }

            // Timer
            val timerColor = when {
                state.phase == MatchPhase.FINISHED -> KendoRed
                state.phase == MatchPhase.ENCHO || state.phase == MatchPhase.ENCHO_PAUSED -> KendoGold
                state.remainingMs < 30_000L -> KendoRed
                else -> KendoGold
            }
            Text(
                text = if (state.phase == MatchPhase.ENCHO || state.phase == MatchPhase.ENCHO_PAUSED)
                    "E  ${TimeFormatter.formatMs(state.enchoElapsedMs)}"
                else TimeFormatter.formatMs(state.remainingMs),
                fontSize = 72.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, color = timerColor, textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (state.phase == MatchPhase.ENCHO || state.phase == MatchPhase.ENCHO_PAUSED)
                Text("ENCHO", color = KendoGold, style = MaterialTheme.typography.titleMedium)
            if (state.phase == MatchPhase.FINISHED)
                Text("Time's up!", color = KendoRed, style = MaterialTheme.typography.titleMedium)

            // Control buttons
            MatchControlButtons(state, vm::startMatch, vm::pause, vm::resumeEncho, vm::startEncho,
                { showResetDialog = true })

            // Scoring + undo
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ScoringPanel(PlayerSide.WHITE, state.whitePoints, state.whiteHansoku, scoringEnabled,
                    { vm.addPoint(PlayerSide.WHITE, it) }, { vm.addHansoku(PlayerSide.WHITE) }, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = vm::undoLastEvent, enabled = state.events.isNotEmpty(),
                        modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo",
                            tint = if (state.events.isNotEmpty()) KendoGold
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    }
                    Text("undo", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                ScoringPanel(PlayerSide.RED, state.redPoints, state.redHansoku, scoringEnabled,
                    { vm.addPoint(PlayerSide.RED, it) }, { vm.addHansoku(PlayerSide.RED) }, Modifier.weight(1f))
            }

            // Score display
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreDisplay(state.whitePoints, state.whiteHansoku, PlayerSide.WHITE, Modifier.weight(1f))
                ScoreDisplay(state.redPoints,   state.redHansoku,   PlayerSide.RED,   Modifier.weight(1f))
            }

            // Notes
            OutlinedTextField(value = state.notes, onValueChange = vm::updateNotes,
                label = { Text("Match Notes (auto-filled, editable)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), maxLines = 8)

            // Submit
            Button(
                onClick  = ::doSubmit,
                enabled  = !state.hasSubmittedResults,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = KendoGold,
                    contentColor           = KendoDark,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    if (state.hasSubmittedResults) "Submitted ✓" else "Submit Results",
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun MatchControlButtons(
    state: com.honbu.app.viewmodel.MatchState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResumeEncho: () -> Unit,
    onStartEncho: () -> Unit,
    onReset: () -> Unit,
) {
    when (state.phase) {
        MatchPhase.IDLE ->
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Start Match", style = MaterialTheme.typography.titleMedium) }

        MatchPhase.RUNNING ->
            Button(onClick = onPause, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("Pause") }

        MatchPhase.PAUSED ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStart, modifier = Modifier.weight(1f).height(52.dp)) { Text("Resume") }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KendoRed)) { Text("Reset") }
            }

        MatchPhase.FINISHED ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KendoRed)) { Text("Reset") }
                Button(onClick = onStartEncho, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KendoGold, contentColor = KendoDark)) {
                    Text("Encho") }
            }

        MatchPhase.ENCHO ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPause, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("Pause") }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KendoRed)) { Text("Reset") }
            }

        MatchPhase.ENCHO_PAUSED ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onResumeEncho, modifier = Modifier.weight(1f).height(52.dp)) { Text("Resume") }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KendoRed)) { Text("Reset") }
            }
    }
}
