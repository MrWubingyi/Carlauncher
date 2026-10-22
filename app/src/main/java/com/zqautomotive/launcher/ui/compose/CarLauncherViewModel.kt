package com.zqautomotive.launcher.ui.compose

import com.zqautomotive.vehicle.control.*
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zqautomotive.launcher.data.VehicleRepository
import com.zqautomotive.launcher.model.VehicleState
import com.zqautomotive.launcher.service.VehicleSendService
import com.zqautomotive.launcher.ui.CockpitUiState
import com.zqautomotive.launcher.ui.home.cards.HomeCardModules
import com.zqautomotive.launcher.ui.home.host.LauncherConnection
import com.zqautomotive.launcher.ui.home.host.VehicleServiceConnection
import com.zqautomotive.launcher.ui.home.map.MapTask
import com.zqautomotive.launcher.ui.home.map.MapTaskFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns host lifecycle and connections; card modules own presentation behavior. */
class CarLauncherViewModel(private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    val cards = HomeCardModules(savedState)
    val uiState = cards.model.uiState
    private val mutableHostStarted = MutableStateFlow(false)
    val hostStarted = mutableHostStarted.asStateFlow()
    private var connection: LauncherConnection? = null
    private var ticker: Job? = null
    private var mapTaskCreated = false
    private var mapTaskFactory: MapTaskFactory = MapTaskFactory { null }
    private val mutableMapTask = MutableStateFlow<MapTask?>(null)
    val mapTask = mutableMapTask.asStateFlow()

    /** Factories contain no Activity/View. Install before the first host starts. */
    fun configureMapTask(factory: MapTaskFactory) {
        check(!mapTaskCreated) { "Map task already created" }
        mapTaskFactory = factory
    }

    fun onHostStart(context: Context) {
        if (connection == null) {
            connection = VehicleServiceConnection(context.applicationContext,
                ::attachService, ::detachService, ::refresh)
        }
        startHost()
    }

    internal fun startHost(hostConnection: LauncherConnection? = connection) {
        if (mutableHostStarted.value) return
        connection = hostConnection
        if (!mapTaskCreated) {
            mutableMapTask.value = mapTaskFactory.create()
            mapTaskCreated = true
        }
        mutableHostStarted.value = true
        connection?.onStart()
        mapTask.value?.onStart()
        ticker = viewModelScope.launch {
            while (true) {
                delay(1_000)
                tick()
            }
        }
    }

    fun onHostStop() {
        if (!mutableHostStarted.value) return
        mutableHostStarted.value = false
        ticker?.cancel()
        ticker = null
        mapTask.value?.onStop()
        connection?.onStop()
    }

    val isVehicleConnected: Boolean get() = uiState.value.isServiceBound

    fun toggleConnection(): String? {
        if (!mutableHostStarted.value) return "桌面尚未启动"
        val activeConnection = connection ?: return "车辆连接尚未初始化"
        if (!isVehicleConnected) setDemo(false)
        return activeConnection.toggle()
    }

    fun tick() {
        refresh()
        cards.media.presenter.tick()
    }

    override fun onCleared() {
        onHostStop()
        mapTask.value?.close()
        mutableMapTask.value = null
        connection = null
    }

    fun attachService(service: VehicleSendService?) = cards.vehicle.presenter.attachService(service)
    fun detachService() = cards.vehicle.presenter.detachService()
    fun refresh() = cards.vehicle.presenter.refresh()
    fun requestControl(action: VehicleAction) = cards.vehicle.presenter.requestControl(action)
    fun setDemo(enabled: Boolean) = cards.vehicle.presenter.setDemo(enabled)
    fun setGear(gear: String) = cards.vehicle.presenter.setGear(gear)
    fun setAutomatic(value: Boolean) = cards.vehicle.presenter.setAutomatic(value)
    fun setPassengerTemp(value: Int) = cards.vehicle.presenter.setPassengerTemp(value)
    fun setDriverTemp(value: Int) = cards.vehicle.presenter.setDriverTemp(value)
    fun toggleCoolWarm(value: Boolean) = cards.vehicle.presenter.toggleCoolWarm(value)
    fun toggleAutoDrive() = cards.vehicle.presenter.toggleAutoDrive()
    fun toggleLights() = cards.vehicle.presenter.toggleLights()
    fun toggleLock() = cards.vehicle.presenter.toggleLock()
    fun togglePlayPause() = cards.media.presenter.togglePlayPause()
    fun toggleFavorite() = cards.media.presenter.toggleFavorite()
    fun toggleRepeat() = cards.media.presenter.toggleRepeat()
    fun toggleMute() = cards.media.presenter.toggleMute()
    fun selectTrack(index: Int) = cards.media.presenter.selectTrack(index)
    fun nextTrack() = cards.media.presenter.nextTrack()
    fun previousTrack() = cards.media.presenter.previousTrack()
    fun seek(progress: Float) = cards.media.presenter.seek(progress)
    fun selectNavItem(index: Int) {
        if (index !in 0..4) return
        savedState["tab"] = index
        cards.model.mutableState.update { it.copy(selectedNavIndex = index) }
    }
}
