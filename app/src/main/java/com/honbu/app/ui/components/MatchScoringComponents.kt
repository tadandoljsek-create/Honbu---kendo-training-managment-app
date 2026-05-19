package com.honbu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honbu.app.ui.theme.*
import com.honbu.app.viewmodel.MatchEvent
import com.honbu.app.viewmodel.PlayerSide

@Composable
fun PlayerDropdown(
    selected: String,
    names: List<String>,
    onSelect: (String) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            border  = androidx.compose.foundation.BorderStroke(2.dp, accentColor),
            colors  = ButtonDefaults.outlinedButtonColors(
                containerColor = accentColor.copy(alpha = 0.12f),
                contentColor   = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected.ifEmpty { "Select player" }, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (names.isEmpty()) {
                DropdownMenuItem(
                    text    = { Text("No members — add in Settings") },
                    onClick = { expanded = false }
                )
            } else {
                names.forEach { name ->
                    DropdownMenuItem(
                        text    = { Text(name) },
                        onClick = { onSelect(name); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ScoringPanel(
    side: PlayerSide,
    points: List<MatchEvent>,
    hansoku: Int,
    enabled: Boolean,
    onPoint: (String) -> Unit,
    onHansoku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (side == PlayerSide.WHITE) WhitePlayerColor else RedPlayerColor
    val bg     = if (side == PlayerSide.WHITE) WhitePlayerBg    else RedPlayerBg
    val maxed  = points.size >= 2

    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "K", "D").forEach { tech ->
                ScoringButton(tech, accent, enabled && !maxed) { onPoint(tech) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ScoringButton("T", accent, enabled && !maxed) { onPoint("T") }
            ScoringButton("h", KendoGold, enabled && hansoku < com.honbu.app.viewmodel.MatchState.MAX_HANSOKU, onHansoku)
        }
    }
}

@Composable
fun ScoringButton(
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick        = onClick,
        enabled        = enabled,
        colors         = ButtonDefaults.outlinedButtonColors(
            contentColor         = accent,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ),
        border         = androidx.compose.foundation.BorderStroke(
            1.5.dp, if (enabled) accent else MaterialTheme.colorScheme.outline
        ),
        contentPadding = PaddingValues(0.dp),
        modifier       = Modifier.size(46.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ScoreDisplay(
    points: List<MatchEvent>,
    hansoku: Int,
    side: PlayerSide,
    modifier: Modifier = Modifier,
) {
    val accent   = if (side == PlayerSide.WHITE) WhitePlayerColor else RedPlayerColor
    val onAccent = if (side == PlayerSide.WHITE) Color.Black       else Color.White

    Column(
        modifier            = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { i ->
                val pt = points.getOrNull(i)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (pt != null) accent else Color.Transparent, RoundedCornerShape(5.dp))
                        .border(1.5.dp, accent, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (pt != null)
                        Text(pt.type, color = onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        if (hansoku > 0) {
            Text("h".repeat(hansoku), color = KendoGold,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}
