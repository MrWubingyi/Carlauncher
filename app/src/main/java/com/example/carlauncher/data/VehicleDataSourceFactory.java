package com.example.carlauncher.data;

import android.content.Context;

import com.example.carlauncher.data.mock.MockVehicleDataSource;

public class VehicleDataSourceFactory {
    private VehicleDataSourceFactory() {
    }

    public static VehicleDataSource create(
            Context context,
            SourceType sourceType
    ) {
        switch (sourceType) {
//            case VHAL:
//                return new VhalVehicleDataSource(context);

            case MOCK:
            default:
                return new MockVehicleDataSource();
        }
    }
}
