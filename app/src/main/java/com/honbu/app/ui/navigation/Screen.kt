package com.honbu.app.ui.navigation

sealed class Screen(val route: String) {
    object MainMenu      : Screen("main_menu")
    object Stopwatch     : Screen("stopwatch")
    object DownTimer     : Screen("down_timer")
    object IntervalTimer : Screen("interval_timer")
    object MatchTimer    : Screen("match_timer")
    object Pools         : Screen("pools")
    object Settings      : Screen("settings")
}
