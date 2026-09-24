package com.example.carlauncher.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.ui.CockpitUiState

class VehicleRepository {

    private val uiState: MutableLiveData<CockpitUiState?> =
        MutableLiveData<CockpitUiState?>(CockpitUiState.initial())
    private var vehicleService: VehicleSendService? = null

    fun getUiState(): LiveData<CockpitUiState?> = uiState

    fun attachService(service: VehicleSendService?) {
        vehicleService = service
        refresh()
    }

    fun detachService() {
        vehicleService = null
        uiState.setValue(CockpitUiState.initial())
    }

    fun refresh() {
        val service = vehicleService
        if (service == null) {
            uiState.setValue(CockpitUiState.initial())
            return
        }
        uiState.setValue(
            CockpitUiState(
                service!!.getLatestVehicleState(),
                service!!.getSourceStatus(),
                true,
                service!!.someipStatus,
                service!!.dataValidity,
            )
        )
    }
}
