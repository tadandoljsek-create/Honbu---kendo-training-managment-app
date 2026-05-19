package com.honbu.app.ui.screens.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honbu.app.ui.theme.KendoGold
import com.honbu.app.ui.theme.KendoRed
import com.honbu.app.util.TimeFormatter
import com.honbu.app.viewmodel.StopwatchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(onBack: () -> Unit) {
    val vm: StopwatchViewModel = viewModel()
    val elapsed   by vm.elapsedMs.collectAsState()
    val isRunning by vm.isRunning.collectAsState()

    val view = LocalView.current
    DisposableEffect(isRunning) {
        view.keepScreenOn = isRunning
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stopwatch") },
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text  = TimeFormatter.formatMsTenths(elapsed),
                fontSize = 76.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = KendoGold
            )

            Spacer(Modifier.height(48.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isRunning) {
                    Button(
                        onClick = vm::pause,
                        colors  = ButtonDefaults.buttonColors(containerColor = KendoRed),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("Pause") }
                } else {
                    Button(
                        onClick  = vm::start,
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
