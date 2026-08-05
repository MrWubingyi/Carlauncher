package com.example.carlauncher.data;

import android.content.Context;

import com.example.carlauncher.data.mock.MockVehicleDataSource;
import com.example.carlauncher.data.vhal.VhalVehicleDataSource;

public final class VehicleDataSourceFactory {
    private VehicleDataSourceFactory() {
    }

    public static VehicleDataSource create(
            Context context,
            SourceType sourceType
    ) {
        if (sourceType == SourceType.VHAL) {
            return new VhalVehicleDataSource(context);
        }
        return new MockVehicleDataSource();
    }
}