package com.zqautomotive.launcher.ui.home.cards

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import com.zqautomotive.launcher.ui.compose.CarLauncherUiState
import com.zqautomotive.launcher.ui.compose.components.*
import com.zqautomotive.launcher.ui.compose.designsystem.HuColors

/** A module assembles a model, presenter and Compose view without owning the host. */
interface HomeCardModule<P> {
    val model: HomeCardModel
    val presenter: P
}

class HomeCardModules(savedState: SavedStateHandle) {
    val model = HomeCardModel(savedState)
    val vehicle = VehicleHomeCardModule(model)
    val media = MediaHomeCardModule(model)
    val map = MapHomeCardModule()
}

class MediaHomeCardModule(override val model: HomeCardModel) : HomeCardModule<MediaCardPresenter> {
    override val presenter = MediaCardPresenter(model)

    @Composable
    fun Content(state: CarLauncherUiState, modifier: Modifier, onQueue: () -> Unit) {
        MediaCard(state, modifier, presenter::togglePlayPause, presenter::nextTrack,
            presenter::previousTrack, presenter::toggleFavorite, presenter::toggleRepeat,
            presenter::toggleMute, onQueue, presenter::seek)
    }
}

class VehicleHomeCardModule(override val model: HomeCardModel) : HomeCardModule<VehicleCardPresenter> {
    override val presenter = VehicleCardPresenter(model)

    @Composable
    fun Content(
        state: CarLauncherUiState,
        modifier: Modifier,
        header: Dp,
        climate: Dp,
        footer: Dp,
        onOpenSettings: () -> Unit,
        onOpenControl: () -> Unit,
    ) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyStatus(state, Modifier.fillMaxWidth().height(header))
            ClimatePanel(
                state,
                Modifier.fillMaxWidth().height(climate),
                presenter::toggleCoolWarm,
                presenter::toggleAutoDrive,
                presenter::setPassengerTemp,
                presenter::setDriverTemp,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HuCard(
                    state,
                    Modifier.fillMaxSize(),
                    presenter::setGear,
                    presenter::toggleLights,
                    presenter::toggleLock,
                    onOpenSettings,
                )
                TextButton(
                    onOpenControl,
                    Modifier.align(Alignment.TopStart).padding(start = 10.dp),
                ) {
                    Text(
                        if (state.isDemo) "DEMO · 演示模式"
                        else if (state.hasVehicleData) state.connectionLabel else "等待车辆数据",
                        color = HuColors.TextDim,
                        fontSize = 10.sp,
                    )
                }
            }
            BottomDriveModeCard(
                state,
                Modifier.fillMaxWidth().height(footer),
                presenter::setAutomatic,
            )
        }
    }
}

/** Offline map remains a local card until a platform task adapter is supplied. */
class MapHomeCardModule {
    @Composable
    fun Content(
        modifier: Modifier,
        fullScreen: Boolean = false,
        bottomClearance: Dp = 0.dp,
        onFullScreen: () -> Unit,
        taskContent: (@Composable () -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit = {},
    ) {
        MapCard(modifier, fullScreen, bottomClearance, onFullScreen, taskContent, content)
    }
}
