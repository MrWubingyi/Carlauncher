package com.example.carlauncher.ui.home.cards

import com.example.carlauncher.ui.compose.CarLauncherUiState
import kotlinx.coroutines.flow.update

/** Demo playback only; no audio engine or vehicle connection. */
class MediaCardPresenter(private val model: HomeCardModel) {
    private val _uiState get() = model.mutableState
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
        _uiState.update {
            when {
                !it.isPlaying -> it
                it.positionSeconds < it.track.seconds -> it.copy(positionSeconds = it.positionSeconds + 1)
                it.isRepeat -> it.copy(positionSeconds = 0)
                else -> it.copy(trackIndex = (it.trackIndex + 1) % CarLauncherUiState.tracks.size, positionSeconds = 0)
            }
        }
    }
}
