package com.example.carlauncher.ui.compose

import androidx.lifecycle.ViewModel
import com.example.carlauncher.data.VehicleRepository
import com.example.carlauncher.service.VehicleSendService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Compose 版车载仪表盘的 UI 状态
 * 使用 StateFlow 替代 LiveData，与 Compose 的 collectAsState 配合使用
 */
data class CarLauncherUiState(
    // ── 车辆仪表 ──
    val speedKph: Int = 125,
    val rpm: Int = 3500,
    val gear: String = "D",
    val batteryPercent: Int = 72,
    val fuelPercent: Int = 83,

    // ── 空调 ──
    val passengerTemp: Int = 20,
    val driverTemp: Int = 20,
    val isCool: Boolean = true,
    val isAutoDrive: Boolean = false,

    // ── 媒体 ──
    val songTitle: String = "Hymn For The Weekend",
    val artist: String = "Coldplay",
    val mediaProgress: Float = 0.63f,
    val currentTime: String = "02:41",
    val totalTime: String = "04:21",
    val isPlaying: Boolean = true,

    // ── 导航 ──
    val locationName: String = "DLF Mall of India, Noida Sector-18",
    val selectedNavIndex: Int = 0,

    // ── 天气 ──
    val temperature: Int = 22,
    val weatherDesc: String = "Cloudy",

    // ── 连接状态与驾驶模式 ──
    val isServiceBound: Boolean = false,
    val connectionLabel: String = "DISCONNECTED",
    val isAutomatic: Boolean = true,
)

/**
 * CarLauncherViewModel — 基于 StateFlow 的 ViewModel
 *
 * 架构: @Composable → StateFlow → ViewModel → VehicleRepository → 中间件实时状态
 */
class CarLauncherViewModel : ViewModel() {

    private val repository: VehicleRepository = VehicleRepository()

    private val _uiState = MutableStateFlow(CarLauncherUiState())
    val uiState: StateFlow<CarLauncherUiState> = _uiState.asStateFlow()

    // ── 车辆服务绑定 ──

    fun attachService(service: VehicleSendService?) {
        repository.attachService(service)
        syncFromRepository()
    }

    fun detachService() {
        repository.detachService()
        _uiState.update { it.copy(isServiceBound = false, connectionLabel = "DISCONNECTED") }
    }

    fun refresh() {
        repository.refresh()
        syncFromRepository()
    }

    private fun syncFromRepository() {
        val repoState = repository.getUiState().value ?: return
        val vehicle = repoState.vehicleState

        _uiState.update { current ->
            current.copy(
                speedKph = vehicle?.vehSpeedKph ?: current.speedKph,
                rpm = vehicle?.engRpm ?: current.rpm,
                gear = vehicle?.gear?.toString() ?: current.gear,
                batteryPercent = vehicle?.soc ?: current.batteryPercent,
                isServiceBound = repoState.isServiceBound,
                connectionLabel = repoState.connectionLabel,
            )
        }
    }

    // ── 档位与驾驶模式 ──

    fun setGear(gear: String) {
        _uiState.update { it.copy(gear = gear) }
    }

    fun setAutomatic(isAutomatic: Boolean) {
        _uiState.update { it.copy(isAutomatic = isAutomatic) }
    }

    // ── 空调控制 ──

    fun setPassengerTemp(temp: Int) {
        _uiState.update { it.copy(passengerTemp = temp.coerceIn(16, 30)) }
    }

    fun setDriverTemp(temp: Int) {
        _uiState.update { it.copy(driverTemp = temp.coerceIn(16, 30)) }
    }

    fun toggleCoolWarm(isCool: Boolean) {
        _uiState.update { it.copy(isCool = isCool) }
    }

    fun toggleAutoDrive() {
        _uiState.update { it.copy(isAutoDrive = !it.isAutoDrive) }
    }

    // ── 媒体控制 ──

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun nextTrack() {
        _uiState.update {
            it.copy(
                songTitle = "Adventure of a Lifetime",
                artist = "Coldplay",
                mediaProgress = 0f,
                currentTime = "00:00",
                totalTime = "04:23",
            )
        }
    }

    fun previousTrack() {
        _uiState.update {
            it.copy(
                songTitle = "Yellow",
                artist = "Coldplay",
                mediaProgress = 0f,
                currentTime = "00:00",
                totalTime = "04:29",
            )
        }
    }

    // ── 导航 ──

    fun selectNavItem(index: Int) {
        _uiState.update { it.copy(selectedNavIndex = index) }
    }
}

