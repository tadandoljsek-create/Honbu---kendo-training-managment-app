package com.honbu.app.ui.screens.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honbu.app.ui.theme.KendoGold
import com.honbu.app.ui.theme.KendoRed
import com.honbu.app.util.TimeFormatter
import com.honbu.app.viewmodel.IntervalPhase
import com.honbu.app.viewmodel.IntervalTimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalTimerScreen(onBack: () -> Unit) {
    val vm: IntervalTimerViewModel = viewModel()
    val state     by vm.state.collectAsState()
    val isRunning by vm.isRunning.collectAsState()

    val view = LocalView.current
    DisposableEffect(isRunning) {
        view.keepScreenOn = isRunning
        onDispose { view.keepScreenOn = false }
    }

    // Settings local UI state (defaults match ViewModel defaults)
    var startMins by remember { mutableStateOf("0") }
    var startSecs by remember { mutableStateOf("00") }
    var basicMins by remember { mutableStateOf("2") }
    var basicSecs by remember { mutableStateOf("00") }
    var switchSecs by remember { mutableStateOf("20") }
    var useReps   by remember { mutableStateOf(false) }
    var repCount  by remember { mutableStateOf("3") }

    fun pushSettings() {
        val startMs = ((startMins.toIntOrNull() ?: 0) * 60L + (startSecs.toIntOrNull() ?: 0)) * 1000L
        val basicMs = ((basicMins.toIntOrNull() ?: 2) * 60L + (basicSecs.toIntOrNull() ?: 0)) * 1000L
        val swMs    = (switchSecs.toIntOrNull() ?: 20) * 1000L
        val reps    = repCount.toIntOrNull() ?: 3
        vm.updateSettings(startMs, basicMs, swMs, useReps, reps)
    }

    LaunchedEffect(Unit) { pushSettings() }

    val (phaseColor, phaseLabel) = when (state.phase) {
        IntervalPhase.STARTING  -> KendoGold  to "Get Ready…"
        IntervalPhase.BASIC     -> Color(0xFF4CAF50) to
            "Work — Rep ${state.currentRep + 1}${if (state.useReps) "/${state.repCount}" else ""}"
        IntervalPhase.SWITCH    -> KendoRed   to "Switch!"
        IntervalPhase.COMPLETE  -> Color.Gray to "Complete!"
        IntervalPhase.IDLE      -> KendoGold  to "Ready"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interval Timer") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Phase card ─────────────────────────────────────────
            Card(
                colors   = CardDefaults.cardColors(containerColor = phaseColor.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text     = phaseLabel,
                    style    = MaterialTheme.typography.titleLarge,
                    color    = phaseColor,
                    modifier = Modifier.padding(14.dp).align(Alignment.CenterHorizontally)
                )
            }

            // ── Timer ──────────────────────────────────────────────
            Text(
                text       = TimeFormatter.formatMs(state.remainingMs),
                fontSize   = 80.sp,
                fontFamily = FontFamily.Monospace,
                color      = phaseColor
            )

            // ── Controls ───────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isRunning) {
                    Button(
                        onClick  = vm::pause,
                        colors   = ButtonDefaults.buttonColors(containerColor = KendoRed),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Pause") }
                } else {
                    Button(
                        onClick  = { pushSettings(); vm.start() },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(
                            when (state.phase) {
                                IntervalPhase.IDLE, IntervalPhase.COMPLETE -> "Start"
                                else -> "Resume"
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick  = vm::reset,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("Reset") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Settings ───────────────────────────────────────────
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = KendoGold)

            SettingDurationRow("Starting Interval", startMins, startSecs,
                { startMins = it }, { startSecs = it })
            SettingDurationRow("Work Interval", basicMins, basicSecs,
                { basicMins = it }, { basicSecs = it })

            OutlinedTextField(
                value = switchSecs, onValueChange = { switchSecs = it },
                label = { Text("Switch Time (sec)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = useReps, onCheckedChange = { useReps = it })
                Text("Limit repetitions", modifier = Modifier.weight(1f))
                if (useReps) {
                    OutlinedTextField(
                        value = repCount, onValueChange = { repCount = it },
                        label = { Text("Reps") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingDurationRow(
    label: String,
    mins: String, secs: String,
    onMins: (String) -> Unit, onSecs: (String) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = mins, onValueChange = onMins,
            label = { Text("Min") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Text(":", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = secs, onValueChange = onSecs,
            label = { Text("Sec") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}
