package com.example.carlauncher.data;

import com.example.carlauncher.service.VehicleSendService;
import com.example.carlauncher.ui.CockpitUiState;

public final class VehicleRepository {

    private VehicleSendService vehicleService;
    private boolean serviceBound;

    public void attachService(VehicleSendService service) {
        vehicleService = service;
        serviceBound = service != null;
    }

    public void detachService() {
        vehicleService = null;
        serviceBound = false;
    }

    public CockpitUiState getCurrentUiState() {
        VehicleSendService service = vehicleService;

        if (!serviceBound || service == null) {
            return CockpitUiState.initial();
        }

        return new CockpitUiState(
                service.getLatestVehicleState(),
                service.getSourceStatus(),
                true,
                service.isConnecting(),
                service.isTcpConnected(),
                service.isSending()
        );
    }
}