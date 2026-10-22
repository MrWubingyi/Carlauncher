package com.example.carlauncher.ui.compose

import com.example.vehicle.control.VehicleControlSnapshot
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.ui.CockpitUiState

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
