package com.honbu.app.ui.screens.pools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honbu.app.ui.theme.*
import com.honbu.app.viewmodel.PoolMatch
import com.honbu.app.viewmodel.PoolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolsListScreen(vm: PoolsViewModel, onBack: () -> Unit) {
    val matches by vm.matches.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset pool?") },
            text  = { Text("This will clear the schedule and all results, returning you to setup.") },
            confirmButton = { TextButton(onClick = { vm.resetPool(); showResetDialog = false },
                colors = ButtonDefaults.textButtonColors(contentColor = KendoRed)) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Pool Schedule  (${matches.size} matches)") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )},
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedButton(
                    onClick  = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = KendoRed)
                ) { Text("Reset Pool") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding  = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(matches, key = { _, m -> m.id }) { index, match ->
                MatchListItem(
                    index     = index,
                    match     = match,
                    total     = matches.size,
                    onGoto    = { vm.goToMatch(index) },
                    onMoveUp  = { vm.moveMatchUp(index) },
                    onMoveDown= { vm.moveMatchDown(index) }
                )
            }
        }
    }
}

@Composable
private fun MatchListItem(
    index: Int,
    match: PoolMatch,
    total: Int,
    onGoto: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        colors    = CardDefaults.cardColors(
            // Use explicit theme colors (no alpha blending) so contents render
            // identically whether the item was initially visible or recomposed
            // after scrolling — no LazyColumn redraw color shift.
            containerColor = if (match.submitted) KendoSurface else KendoSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Match number — compact to prevent two-digit wrap
            Text(
                "${index + 1}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 22.dp, max = 28.dp)
            )

            // Players + result
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        match.whitePlayer.ifEmpty { "White" },
                        fontWeight = FontWeight.SemiBold,
                        color = com.honbu.app.ui.theme.WhitePlayerColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("  vs  ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        match.redPlayer.ifEmpty { "Red" },
                        fontWeight = FontWeight.SemiBold,
                        color = KendoRed,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (match.submitted) {
                    val wStr = if (match.whiteResults.isEmpty()) "-" else match.whiteResults.joinToString(",")
                    val rStr = if (match.redResults.isEmpty())   "-" else match.redResults.joinToString(",")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("W: $wStr", fontSize = 12.sp,
                            color = com.honbu.app.ui.theme.WhitePlayerColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium)
                        Text("R: $rStr", fontSize = 12.sp,
                            color = KendoRed.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Up / Down
            Column {
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move up",
                        tint = if (index > 0) KendoGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move down",
                        tint = if (index < total - 1) KendoGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(20.dp))
                }
            }

            // Goto button
            FilledIconButton(
                onClick  = onGoto,
                modifier = Modifier.size(40.dp),
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (match.submitted) MaterialTheme.colorScheme.outline
                                     else KendoRed)
            ) {
                Icon(Icons.Default.PlayArrow, "Go to match", modifier = Modifier.size(20.dp))
            }
        }
    }
}
