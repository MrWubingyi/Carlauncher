package com.example.carlauncher.ui.compose

import com.example.vehicle.control.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.carlauncher.data.VehicleRepository
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.ui.CockpitUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DemoTrack(val title: String, val artist: String, val seconds: Int)

data class CarLauncherUiState(
    val speedKph: Int = 125,
    val rpm: Int = 3500,
    val gear: String = "D",
    val batteryPercent: Int = 72,
    val fuelPercent: Int = 83,
    val passengerTemp: Int = 20,
    val driverTemp: Int = 20,
    val isCool: Boolean = true,
    val isAutoDrive: Boolean = false,
    val isAutomatic: Boolean = true,
    val isLightOn: Boolean = true,
    val isLocked: Boolean = true,
    val isDemo: Boolean = true,
    val hasVehicleData: Boolean = false,
    val isServiceBound: Boolean = false,
    val connectionLabel: String = "SERVICE UNBOUND",
    val cockpit: CockpitUiState? = null,
    val controls: VehicleControlSnapshot = VehicleControlSnapshot(),
    val vehicle: VehicleState? = null,
    val selectedNavIndex: Int = 0,
    val locationName: String = "DLF Mall of India, Noida Sector-18",
    val trackIndex: Int = 0,
    val positionSeconds: Int = 161,
    val isPlaying: Boolean = false,
    val favoriteTracks: Set<Int> = emptySet(),
    val isRepeat: Boolean = false,
    val isMuted: Boolean = false,
) {
    val track get() = tracks[trackIndex]
    val isFavorite get() = trackIndex in favoriteTracks
    val songTitle get() = track.title
    val artist get() = track.artist
    val mediaProgress get() = positionSeconds.toFloat() / track.seconds
    val currentTime get() = formatDuration(positionSeconds)
    val totalTime get() = formatDuration(track.seconds)
    val showVehicleValues get() = isDemo || hasVehicleData

    companion object {
        val tracks = listOf(
            DemoTrack("Hymn For The Weekend", "Coldplay", 261),
            DemoTrack("Adventure of a Lifetime", "Coldplay", 263),
            DemoTrack("Yellow", "Coldplay", 269),
        )
        fun formatDuration(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

/** Demo controls stay local; explicit live actions use the injected SOME/IP source. */
class CarLauncherViewModel(private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val repository by lazy { VehicleRepository() }
    private val _uiState = MutableStateFlow(CarLauncherUiState(
        passengerTemp = savedState["passenger"] ?: 20,
        driverTemp = savedState["driver"] ?: 20,
        isCool = savedState["cool"] ?: true,
        isAutomatic = savedState["automatic"] ?: true,
        isDemo = savedState["demo"] ?: true,
        selectedNavIndex = savedState["tab"] ?: 0,
    ))
    val uiState = _uiState.asStateFlow()

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
    fun togglePlayPause() { _uiState.update { it.copy(isPlaying = !it.isPlaying) } }
    fun toggleFavorite() {
        _uiState.update { it.copy(favoriteTracks = if (it.isFavorite) it.favoriteTracks - it.trackIndex else it.favoriteTracks + it.trackIndex) }
    }
    fun toggleRepeat() { _uiState.update { it.copy(isRepeat = !it.isRepeat) } }
    fun toggleMute() { _uiState.update { it.copy(isMuted = !it.isMuted) } }
    fun selectTrack(index: Int) {
        if (index !in CarLauncherUiState.tracks.indices) return
        _uiState.update { it.copy(trackIndex = index, positionSeconds = 0) }
    }
    fun nextTrack() = selectTrack((_uiState.value.trackIndex + 1) % CarLauncherUiState.tracks.size)
    fun previousTrack() = selectTrack((_uiState.value.trackIndex + CarLauncherUiState.tracks.size - 1) % CarLauncherUiState.tracks.size)
    fun seek(progress: Float) {
        if (!progress.isFinite()) return
        _uiState.update { it.copy(positionSeconds = (progress.coerceIn(0f, 1f) * it.track.seconds).toInt()) }
    }
    fun tick() {
        refresh()
        _uiState.update {
            when {
                !it.isPlaying -> it
                it.positionSeconds < it.track.seconds -> it.copy(positionSeconds = it.positionSeconds + 1)
                it.isRepeat -> it.copy(positionSeconds = 0)
                else -> it.copy(trackIndex = (it.trackIndex + 1) % CarLauncherUiState.tracks.size, positionSeconds = 0)
            }
        }
    }
    fun selectNavItem(index: Int) {
        if (index !in 0..4) return
        savedState["tab"] = index
        _uiState.update { it.copy(selectedNavIndex = index) }
    }
}
