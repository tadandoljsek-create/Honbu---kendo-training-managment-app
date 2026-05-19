package com.honbu.app.util

object TimeFormatter {

    /** Format milliseconds as M:SS */
    fun formatMs(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSeconds = safe / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /** Format milliseconds as M:SS.t (with tenths) */
    fun formatMsTenths(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSeconds = safe / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (safe % 1000) / 100
        return "%d:%02d.%d".format(minutes, seconds, tenths)
    }
}
