package com.example.carlauncher.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.carlauncher.data.VehicleRepository
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.ui.CockpitUiState

class CockpitViewModel : ViewModel() {

    private val repository: VehicleRepository = VehicleRepository()

    val uiState: LiveData<CockpitUiState?>
        get() {
            return repository.getUiState()
        }

    init {
        Log.i(
            "COCKPIT_VIEW_MODEL",
            "created, id=" + System.identityHashCode(this),
        )
    }

    fun attachService(service: VehicleSendService?) {
        repository.attachService(service)
    }

    fun detachService() {
        repository.detachService()
    }

    fun refresh() {
        repository.refresh()
    }

    protected override fun onCleared() {
        Log.i(
            "COCKPIT_VIEW_MODEL",
            "onCleared, id=" + System.identityHashCode(this),
        )
    }
}
