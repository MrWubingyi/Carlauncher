package com.example.carlauncher.model;

import com.example.carlauncher.data.VehicleProtocol;

public final class VehicleStateValidator {
    private VehicleStateValidator() {
    }

    public static DataValidity validate(
            float speedKph,
            Gear gear
    ) {
        if (Float.isNaN(speedKph)
                || Float.isInfinite(speedKph)
                || !VehicleProtocol.isSpeedValid(Math.round(speedKph))) {
            return DataValidity.INVALID_SPEED;
        }

        if (gear == null) {
            return DataValidity.INCOMPLETE;
        }

        return DataValidity.VALID;
    }
}
