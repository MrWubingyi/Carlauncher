package com.zqautomotive.launcher.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Activity selects policy; the view resolves it against actual window constraints. */
enum class HomeLayout {
    Adaptive, Wide, Stacked;

    fun isWide(width: Dp): Boolean = when (this) {
        Adaptive -> width >= 900.dp
        Wide -> true
        Stacked -> false
    }
}
