package com.example.carlauncher.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.carlauncher.data.VehicleRepository;
import com.example.carlauncher.service.VehicleSendService;
import com.example.carlauncher.ui.CockpitUiState;

public final class CockpitViewModel extends ViewModel {

    private final VehicleRepository repository =
            new VehicleRepository();

    public LiveData<CockpitUiState> getUiState() {
        return repository.getUiState();
    }

    public void attachService(VehicleSendService service) {
        repository.attachService(service);
    }

    public void detachService() {
        repository.detachService();
    }

    public void refresh() {
        repository.refresh();
    }
}