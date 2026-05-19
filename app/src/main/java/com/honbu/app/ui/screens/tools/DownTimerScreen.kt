package com.honbu.app.ui.screens.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honbu.app.ui.theme.KendoGold
import com.honbu.app.ui.theme.KendoRed
import com.honbu.app.util.TimeFormatter
import com.honbu.app.viewmodel.DownTimerViewModel

private val PRESETS = listOf(
    "4m"   to 4 * 60_000L,
    "3m"   to 3 * 60_000L,
    "2m"   to 2 * 60_000L,
    "1:30" to 90_000L,
    "1m"   to 60_000L,
    "30s"  to 30_000L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownTimerScreen(onBack: () -> Unit) {
    val vm: DownTimerViewModel = viewModel()
    val remaining  by vm.remainingMs.collectAsState()
    val duration   by vm.durationMs.collectAsState()
    val isRunning  by vm.isRunning.collectAsState()
    val isFinished by vm.isFinished.collectAsState()

    var showCustomDialog by remember { mutableStateOf(false) }
    var customMins by remember { mutableStateOf("3") }
    var customSecs by remember { mutableStateOf("00") }

    // Keep screen on while timer is running
    val view = LocalView.current
    DisposableEffect(isRunning) {
        view.keepScreenOn = isRunning
        onDispose { view.keepScreenOn = false }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom duration") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMins, onValueChange = { customMins = it },
                        label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = customSecs, onValueChange = { customSecs = it },
                        label = { Text("Sec") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ms = ((customMins.toIntOrNull() ?: 0) * 60L +
                              (customSecs.toIntOrNull() ?: 0)) * 1_000L
                    if (ms > 0) vm.setDuration(ms)
                    showCustomDialog = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Down Timer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Presets ─────────────────────────────────────────
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                PRESETS.forEach { (label, ms) ->
                    FilterChip(
                        selected = duration == ms && !isRunning && !isFinished,
                        enabled  = !isRunning,
                        onClick  = { vm.setDuration(ms) },
                        label    = { Text(label) }
                    )
                }
                FilterChip(
                    selected = false,
                    enabled  = !isRunning,
                    onClick  = { showCustomDialog = true },
                    label    = { Text("custom") }
                )
            }

            HorizontalDivider()

            // ── Timer display ────────────────────────────────────
            val timerColor = when {
                isFinished          -> KendoRed
                remaining < 30_000L -> KendoRed
                else                -> KendoGold
            }
            Text(
                text       = TimeFormatter.formatMs(remaining),
                fontSize   = 80.sp,
                fontFamily = FontFamily.Monospace,
                color      = timerColor
            )
            if (isFinished) {
                Text("Time's up!", color = KendoRed, style = MaterialTheme.typography.titleLarge)
            }

            // ── Controls ─────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isRunning) {
                    Button(
                        onClick  = vm::pause,
                        colors   = ButtonDefaults.buttonColors(containerColor = KendoRed),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("Pause") }
                } else {
                    Button(
                        onClick  = vm::start,
                        enabled  = !isFinished && remaining > 0,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("Start") }
                }
                OutlinedButton(
                    onClick  = vm::reset,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("Reset") }
            }
        }
    }
}
