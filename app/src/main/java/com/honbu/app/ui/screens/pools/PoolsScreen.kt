package com.honbu.app.ui.screens.pools

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.honbu.app.viewmodel.PoolsView
import com.honbu.app.viewmodel.PoolsViewModel

@Composable
fun PoolsScreen(onBack: () -> Unit) {
    val vm: PoolsViewModel = viewModel()
    val currentView by vm.currentView.collectAsState()

    // Back button per view
    BackHandler(enabled = currentView != PoolsView.SETUP) {
        when (currentView) {
            PoolsView.LIST  -> vm.backToSetup()
            PoolsView.MATCH -> { /* handled inside PoolMatchTimerScreen */ }
            PoolsView.SETUP -> { /* handled below */ }
        }
    }

    when (currentView) {
        PoolsView.SETUP -> PoolsSetupScreen(vm = vm, onBack = onBack)
        PoolsView.LIST  -> PoolsListScreen (vm = vm, onBack = { vm.backToSetup() })
        PoolsView.MATCH -> PoolMatchTimerScreen(vm = vm)
    }
}
