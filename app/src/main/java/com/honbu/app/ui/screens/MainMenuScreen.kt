package com.honbu.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honbu.app.R
import com.honbu.app.ui.theme.KendoGold
import com.honbu.app.ui.theme.KendoRed
import com.honbu.app.ui.theme.KendoSurfaceVariant

@Composable
fun MainMenuScreen(
    onStopwatch: () -> Unit,
    onDownTimer: () -> Unit,
    onIntervalTimer: () -> Unit,
    onMatchTimer: () -> Unit,
    onPools: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))

        Image(
            painter            = painterResource(R.mipmap.ic_launcher),
            contentDescription = "Honbu logo",
            modifier           = Modifier.size(100.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("HONBU", style = MaterialTheme.typography.headlineLarge,
            color = KendoGold, letterSpacing = 10.sp)
        Text("v1.1.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Text("Kendo Training Manager", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Spacer(Modifier.height(32.dp))

        MenuButton("⏱  Stopwatch",      "Count-up timer",               onStopwatch)
        Spacer(Modifier.height(10.dp))
        MenuButton("⏬  Down Timer",     "Countdown with kendo presets", onDownTimer)
        Spacer(Modifier.height(10.dp))
        MenuButton("🔄  Interval Timer", "Work / switch cycle timer",    onIntervalTimer)
        Spacer(Modifier.height(10.dp))
        MenuButton("⚔  Match Timer",     "Scoring, notes & CSV export",  onMatchTimer)
        Spacer(Modifier.height(10.dp))
        MenuButton("🏆  Pools",          "Round-robin pool scheduling",  onPools)

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = onSettings,
            modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("⚙  Settings") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MenuButton(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val bg      = if (highlight) KendoRed else KendoSurfaceVariant
    val fg      = if (highlight) Color.White else MaterialTheme.colorScheme.onSurface
    Button(
        onClick  = onClick,
        colors   = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape    = MaterialTheme.shapes.medium
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(label,    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,   color = fg.copy(alpha = 0.65f))
        }
    }
}
