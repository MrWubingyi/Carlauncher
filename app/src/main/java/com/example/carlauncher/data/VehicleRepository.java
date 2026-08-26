package com.example.carlauncher.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.carlauncher.service.VehicleSendService;
import com.example.carlauncher.ui.CockpitUiState;

public final class VehicleRepository {

    private final MutableLiveData<CockpitUiState> uiState = new MutableLiveData<>(CockpitUiState.initial());
    private VehicleSendService vehicleService;

    public LiveData<CockpitUiState> getUiState() {
        return uiState;
    }

    public void attachService(VehicleSendService service) {
        vehicleService = service;
        refresh();
    }

    public void detachService() {
        vehicleService = null;
        uiState.setValue(CockpitUiState.initial());
    }

    public void refresh() {
        VehicleSendService service = vehicleService;
        if (service == null) {
            uiState.setValue(CockpitUiState.initial());
            return;
        }
        uiState.setValue(new CockpitUiState(service.getLatestVehicleState(),
                service.getSourceStatus(), true, service.getTcpState(),service.getDataValidity()));

    }

}