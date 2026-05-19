package com.honbu.app.ui.screens.pools

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
import androidx.compose.material.icons.filled.*
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
import com.honbu.app.ui.screens.match.MatchControlButtons
import com.honbu.app.ui.theme.*
import com.honbu.app.util.TimeFormatter
import com.honbu.app.viewmodel.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PoolMatchTimerScreen(vm: PoolsViewModel) {
    val matchIndex    by vm.currentMatchIndex.collectAsState()
    val matches       by vm.matches.collectAsState()
    val poolDuration  by vm.poolDurationMs.collectAsState()
    val poolGeneration by vm.poolGeneration.collectAsState()
    val context = LocalContext.current

    // Single shared MatchTimerViewModel for all pool matches.
    // State is saved/restored explicitly per match via PoolsViewModel.
    val timerVm: MatchTimerViewModel = viewModel(key = "pool_timer")
    val state         by timerVm.state.collectAsState()
    val members       by timerVm.members.collectAsState()
    val submitResult  by timerVm.submitResult.collectAsState()

    val match     = matches.getOrNull(matchIndex) ?: return
    val hasNext   = matchIndex < matches.size - 1
    val hasPrev   = matchIndex > 0
    val nextMatch = matches.getOrNull(matchIndex + 1)
    val memberNames = remember(members) { members.map { it.name } }
    val scoringEnabled = state.phase in MatchState.SCORING_PHASES

    // ── Load/restore state when match changes ──────────────────────
    // poolGeneration in the key ensures a fresh load when the pool is regenerated,
    // even if matchIndex happens to be the same number.
    LaunchedEffect(matchIndex, poolGeneration) {
        val saved = vm.loadMatchState(matchIndex)
        if (saved != null) {
            timerVm.restoreState(saved)
        } else {
            timerVm.resetMatch()
            timerVm.setDuration(poolDuration)
            timerVm.updatePlayerName(PlayerSide.WHITE, match.whitePlayer)
            timerVm.updatePlayerName(PlayerSide.RED,   match.redPlayer)
        }
    }

    // ── Save current match state and navigate ─────────────────────
    fun saveAndGoTo(newIndex: Int) {
        vm.saveMatchState(matchIndex, timerVm.state.value)
        vm.goToMatch(newIndex)
    }

    fun saveAndGoBack() {
        vm.saveMatchState(matchIndex, timerVm.state.value)
        vm.backToList()
    }

    // ── Keep screen on while active ────────────────────────────────
    val view = LocalView.current
    val active = state.phase != MatchPhase.IDLE && state.phase != MatchPhase.FINISHED
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    val needsWarning = state.phase != MatchPhase.IDLE && !state.hasSubmittedResults

    // ── Dialog state ───────────────────────────────────────────────
    var showResetDialog  by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var showBackWarning  by remember { mutableStateOf(false) }
    var showClearDialog  by remember { mutableStateOf(false) }
    var pendingAction    by remember { mutableStateOf<(() -> Unit)?>(null) }
    var customMins by remember { mutableStateOf("3") }
    var customSecs by remember { mutableStateOf("00") }

    fun warnOrDo(action: () -> Unit) {
        if (needsWarning) { pendingAction = action; showBackWarning = true }
        else action()
    }

    BackHandler { warnOrDo { saveAndGoBack() } }

    val writePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted -> if (granted) timerVm.submit(context)
    }
    fun doSubmit() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else timerVm.submit(context)
    }

    // ── Handle submit result ───────────────────────────────────────
    // On success: record result in pool list, show dialog for 2 s then dismiss.
    // On error: dialog stays until user taps OK.
    LaunchedEffect(submitResult) {
        val msg = submitResult ?: return@LaunchedEffect
        if (!msg.startsWith("Error")) {
            val s = timerVm.state.value
            vm.recordResult(matchIndex, s.whitePoints.map { it.type }, s.redPoints.map { it.type })
            delay(2_000)
            timerVm.clearSubmitResult()
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────

    if (showBackWarning) {
        AlertDialog(
            onDismissRequest = { showBackWarning = false },
            title = { Text("Leave without submitting?") },
            text  = { Text("This match has been started but results haven't been submitted yet.") },
            confirmButton = {
                TextButton(onClick = {
                    showBackWarning = false
                    pendingAction?.invoke()
                    pendingAction = null
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { showBackWarning = false }) { Text("Stay") } }
        )
    }

    // Submit result dialog (success for 2 s, error until dismissed)
    submitResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { if (msg.startsWith("Error")) timerVm.clearSubmitResult() },
            title = { Text(if (msg.startsWith("Error")) "Error" else "✓ Saved") },
            text  = { Text(msg) },
            confirmButton = {
                if (msg.startsWith("Error"))
                    TextButton(onClick = timerVm::clearSubmitResult) { Text("OK") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset match?") },
            text  = { Text("Timer and recorded points will be cleared.") },
            confirmButton = {
                TextButton(onClick = {
                    timerVm.resetMatch()
                    showResetDialog = false
                }, colors = ButtonDefaults.textButtonColors(contentColor = KendoRed)) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear match?") },
            text  = { Text("This will reset the timer, all points, and the recorded result in the pool list.") },
            confirmButton = {
                TextButton(onClick = {
                    timerVm.resetMatch()
                    vm.clearResult(matchIndex)
                    vm.clearMatchState(matchIndex)
                    showClearDialog = false
                }, colors = ButtonDefaults.textButtonColors(contentColor = KendoRed)) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
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
            confirmButton = {
                TextButton(onClick = {
                    val ms = ((customMins.toIntOrNull() ?: 0) * 60L + (customSecs.toIntOrNull() ?: 0)) * 1_000L
                    if (ms > 0) timerVm.setDuration(ms)
                    showCustomDialog = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") } }
        )
    }

    // ── Layout ─────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Match ${matchIndex + 1} / ${matches.size}") },
                navigationIcon = {
                    IconButton(onClick = { warnOrDo { saveAndGoBack() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to list")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (needsWarning || match.submitted) showClearDialog = true
                        else { timerVm.resetMatch(); vm.clearResult(matchIndex); vm.clearMatchState(matchIndex) }
                    }) {
                        Icon(Icons.Default.RestartAlt, "Clear match",
                            tint = if (state.phase != MatchPhase.IDLE || match.submitted) KendoRed
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
            )
        }
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
                listOf("30s" to 30_000L,"1m" to 60_000L,"1:30" to 90_000L,
                    "2m" to 2*60_000L,"3m" to 3*60_000L,"4m" to 4*60_000L).forEach { (label, ms) ->
                    FilterChip(selected = state.durationMs == ms && canChange, enabled = canChange,
                        onClick = { timerVm.setDuration(ms) }, label = { Text(label, fontSize = 13.sp) })
                }
                FilterChip(selected = false, enabled = canChange, onClick = { showCustomDialog = true },
                    label = { Text("custom", fontSize = 13.sp) })
            }

            // Player dropdowns
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerDropdown(state.whitePlayer, memberNames, { timerVm.updatePlayerName(PlayerSide.WHITE, it) },
                    WhitePlayerColor, Modifier.weight(1f))
                PlayerDropdown(state.redPlayer, memberNames, { timerVm.updatePlayerName(PlayerSide.RED, it) },
                    RedPlayerColor, Modifier.weight(1f))
            }

            // Timer display
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
                fontSize = 72.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = timerColor, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp)
            )
            if (state.phase == MatchPhase.ENCHO || state.phase == MatchPhase.ENCHO_PAUSED)
                Text("ENCHO", color = KendoGold, style = MaterialTheme.typography.titleMedium)
            if (state.phase == MatchPhase.FINISHED)
                Text("Time's up!", color = KendoRed, style = MaterialTheme.typography.titleMedium)

            // Control buttons
            MatchControlButtons(state, timerVm::startMatch, timerVm::pause, timerVm::resumeEncho,
                timerVm::startEncho, { showResetDialog = true })

            // Scoring + undo
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ScoringPanel(PlayerSide.WHITE, state.whitePoints, state.whiteHansoku, scoringEnabled,
                    { timerVm.addPoint(PlayerSide.WHITE, it) }, { timerVm.addHansoku(PlayerSide.WHITE) }, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = timerVm::undoLastEvent, enabled = state.events.isNotEmpty(),
                        modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo",
                            tint = if (state.events.isNotEmpty()) KendoGold
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    }
                    Text("undo", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                ScoringPanel(PlayerSide.RED, state.redPoints, state.redHansoku, scoringEnabled,
                    { timerVm.addPoint(PlayerSide.RED, it) }, { timerVm.addHansoku(PlayerSide.RED) }, Modifier.weight(1f))
            }

            // Score display
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreDisplay(state.whitePoints, state.whiteHansoku, PlayerSide.WHITE, Modifier.weight(1f))
                ScoreDisplay(state.redPoints,   state.redHansoku,   PlayerSide.RED,   Modifier.weight(1f))
            }

            // Notes
            OutlinedTextField(value = state.notes, onValueChange = timerVm::updateNotes,
                label = { Text("Match Notes (auto-filled, editable)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), maxLines = 8)

            // ── Submit row: ◀ | Submit | ▶ ────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick  = { warnOrDo { saveAndGoTo(matchIndex - 1) } },
                    enabled  = hasPrev,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, "Previous",
                        tint = if (hasPrev) KendoGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp))
                }

                Button(
                    onClick  = ::doSubmit,
                    enabled  = !state.hasSubmittedResults,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = KendoGold,
                        contentColor           = KendoDark,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        if (state.hasSubmittedResults) "Submitted ✓" else "Submit",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                IconButton(
                    onClick  = { warnOrDo { saveAndGoTo(matchIndex + 1) } },
                    enabled  = hasNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, "Next",
                        tint = if (hasNext) KendoGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp))
                }
            }

            // Next match info
            if (nextMatch != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = KendoGold, modifier = Modifier.size(18.dp))
                        Text("Next: ", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${nextMatch.whitePlayer.ifEmpty { "White" }} vs ${nextMatch.redPlayer.ifEmpty { "Red" }}",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
