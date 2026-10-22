package com.zqautomotive.launcher.ui.home.cards

import androidx.lifecycle.SavedStateHandle
import com.zqautomotive.launcher.ui.compose.CarLauncherUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared dashboard snapshot; presenters update only their own fields. */
class HomeCardModel(internal val savedState: SavedStateHandle) {
    internal val mutableState = MutableStateFlow(CarLauncherUiState(
        passengerTemp = savedState["passenger"] ?: 20,
        driverTemp = savedState["driver"] ?: 20,
        isCool = savedState["cool"] ?: true,
        isAutomatic = savedState["automatic"] ?: true,
        isDemo = savedState["demo"] ?: true,
        selectedNavIndex = savedState["tab"] ?: 0,
    ))
    val uiState = mutableState.asStateFlow()

}
