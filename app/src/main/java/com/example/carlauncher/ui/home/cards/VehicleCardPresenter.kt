package com.example.carlauncher.ui.home.cards

import com.example.carlauncher.data.VehicleRepository
import com.example.carlauncher.service.VehicleSendService
import com.example.vehicle.control.VehicleAction
import kotlinx.coroutines.flow.update

class VehicleCardPresenter(private val model: HomeCardModel) {
    private val repository by lazy { VehicleRepository() }
    private val _uiState get() = model.mutableState
    private val savedState get() = model.savedState
    fun attachService(service: VehicleSendService?) {
        repository.attachService(service)
        setDemo(false)
    }
    fun detachService() {
        repository.detachService()
        syncFromRepository()
    }
    fun refresh() {
        repository.refresh()
        syncFromRepository()
    }
    private fun syncFromRepository() {
        val snapshot = repository.getUiState().value ?: return
        _uiState.update { current ->
            val vehicle = snapshot.vehicleState
            val live = !current.isDemo
            current.copy(
                cockpit = snapshot, vehicle = vehicle,
                controls = snapshot.controls,
                isServiceBound = snapshot.isServiceBound,
                connectionLabel = snapshot.connectionLabel,
                hasVehicleData = vehicle != null,
                speedKph = if (live) vehicle?.vehSpeedKph ?: 0 else current.speedKph,
                rpm = if (live) vehicle?.engRpm ?: 0 else current.rpm,
                gear = if (live) vehicle?.gear?.toString() ?: "—" else current.gear,
                batteryPercent = if (live) vehicle?.soc ?: 0 else current.batteryPercent,
                isLightOn = if (live) vehicle?.headlightsState == 1 else current.isLightOn,
                isLocked = if (live) vehicle?.doorLock == true else current.isLocked,
            )
        }
    }
    fun requestControl(action: VehicleAction) {
        if (_uiState.value.isDemo || !_uiState.value.isServiceBound) return
        repository.requestControl(action)
        refresh()
    }

    fun setDemo(enabled: Boolean) {
        savedState["demo"] = enabled
        _uiState.update {
            if (enabled) it.copy(isDemo = true, speedKph = 125, rpm = 3500, gear = "D", batteryPercent = 72)
            else it.copy(isDemo = false, isAutoDrive = false)
        }
        syncFromRepository()
    }
    fun setGear(gear: String) {
        if (_uiState.value.isDemo && gear in listOf("P", "R", "N", "D")) {
            _uiState.update { it.copy(gear = gear, speedKph = if (gear == "D") 125 else 0) }
        }
    }
    fun setAutomatic(value: Boolean) {
        if (!_uiState.value.isDemo) return
        savedState["automatic"] = value
        _uiState.update { it.copy(isAutomatic = value) }
    }
    fun setPassengerTemp(value: Int) {
        if (!_uiState.value.isDemo) return
        savedState["passenger"] = value.coerceIn(16, 30)
        _uiState.update { it.copy(passengerTemp = value.coerceIn(16, 30)) }
    }
    fun setDriverTemp(value: Int) {
        if (!_uiState.value.isDemo) return
        savedState["driver"] = value.coerceIn(16, 30)
        _uiState.update { it.copy(driverTemp = value.coerceIn(16, 30)) }
    }
    fun toggleCoolWarm(value: Boolean) {
        if (!_uiState.value.isDemo) return
        savedState["cool"] = value
        _uiState.update { it.copy(isCool = value) }
    }
    fun toggleAutoDrive() { if (_uiState.value.isDemo) _uiState.update { it.copy(isAutoDrive = !it.isAutoDrive) } }
    fun toggleLights() { if (_uiState.value.isDemo) _uiState.update { it.copy(isLightOn = !it.isLightOn) } }
    fun toggleLock() { if (_uiState.value.isDemo) _uiState.update { it.copy(isLocked = !it.isLocked) } }
}
