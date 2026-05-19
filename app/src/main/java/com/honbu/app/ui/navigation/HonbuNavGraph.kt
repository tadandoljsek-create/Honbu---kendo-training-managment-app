package com.honbu.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.honbu.app.ui.screens.MainMenuScreen
import com.honbu.app.ui.screens.match.MatchTimerScreen
import com.honbu.app.ui.screens.pools.PoolsScreen
import com.honbu.app.ui.screens.settings.SettingsScreen
import com.honbu.app.ui.screens.tools.DownTimerScreen
import com.honbu.app.ui.screens.tools.IntervalTimerScreen
import com.honbu.app.ui.screens.tools.StopwatchScreen

@Composable
fun HonbuNavGraph() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Screen.MainMenu.route) {
        composable(Screen.MainMenu.route) {
            MainMenuScreen(
                onStopwatch     = { nav.navigate(Screen.Stopwatch.route) },
                onDownTimer     = { nav.navigate(Screen.DownTimer.route) },
                onIntervalTimer = { nav.navigate(Screen.IntervalTimer.route) },
                onMatchTimer    = { nav.navigate(Screen.MatchTimer.route) },
                onPools         = { nav.navigate(Screen.Pools.route) },
                onSettings      = { nav.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Stopwatch.route)     { StopwatchScreen    (onBack = { nav.popBackStack() }) }
        composable(Screen.DownTimer.route)     { DownTimerScreen    (onBack = { nav.popBackStack() }) }
        composable(Screen.IntervalTimer.route) { IntervalTimerScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.MatchTimer.route)    { MatchTimerScreen   (onBack = { nav.popBackStack() }) }
        composable(Screen.Pools.route)         { PoolsScreen        (onBack = { nav.popBackStack() }) }
        composable(Screen.Settings.route)      { SettingsScreen     (onBack = { nav.popBackStack() }) }
    }
}
