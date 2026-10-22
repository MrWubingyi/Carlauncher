package com.zqautomotive.launcher.ui.home.cards

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class MediaCardPresenterTest {
    private val cards = HomeCardModules(SavedStateHandle())
    private val presenter = cards.media.presenter
    private val state get() = cards.model.uiState.value

    @Test fun repeatPauseAndQueueWrapPreserveFavorites() {
        presenter.toggleFavorite()
        presenter.toggleRepeat()
        presenter.seek(1f)
        presenter.togglePlayPause()
        presenter.tick()
        assertEquals(0, state.positionSeconds)
        presenter.togglePlayPause()
        presenter.tick()
        assertEquals(0, state.positionSeconds)
        presenter.previousTrack()
        assertEquals(2, state.trackIndex)
        assertFalse(state.isFavorite)
        presenter.nextTrack()
        assertEquals(0, state.trackIndex)
        assertTrue(state.isFavorite)
    }

    @Test fun invalidSeekAndTrackLeaveStateIntactAndPlaybackAdvancesAtEnd() {
        val initial = state
        presenter.seek(Float.NaN)
        presenter.seek(Float.POSITIVE_INFINITY)
        presenter.selectTrack(-1)
        presenter.selectTrack(3)
        assertEquals(initial, state)
        presenter.seek(2f)
        assertEquals(state.track.seconds, state.positionSeconds)
        presenter.togglePlayPause()
        presenter.tick()
        assertEquals(1, state.trackIndex)
        assertEquals(0, state.positionSeconds)
        assertEquals(initial.passengerTemp, state.passengerTemp)
        assertEquals(initial.isDemo, state.isDemo)
    }

    @Test fun moduleRestoresVehicleSettingsWithoutResettingMediaOnVehicleEdits() {
        val modules = HomeCardModules(SavedStateHandle(mapOf("passenger" to 24, "tab" to 4)))
        modules.media.presenter.nextTrack()
        modules.vehicle.presenter.setPassengerTemp(40)
        assertEquals(30, modules.model.uiState.value.passengerTemp)
        assertEquals(1, modules.model.uiState.value.trackIndex)
        assertEquals(4, modules.model.uiState.value.selectedNavIndex)
    }
}
