package com.example.carlauncher.ui.compose.components

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Date

/** Injectable wall clock keeps visual fixtures deterministic without masking the date. */
fun interface DashboardClock {
    fun now(): Date
}

val LocalDashboardClock = staticCompositionLocalOf { DashboardClock { Date() } }
