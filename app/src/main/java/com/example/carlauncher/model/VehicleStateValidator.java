package com.example.carlauncher.model;

public final class VehicleStateValidator {
    private VehicleStateValidator() {
    }

    public static DataValidity validate(
            float speedKph,
            Gear gear
    ) {
        if (Float.isNaN(speedKph)
                || Float.isInfinite(speedKph)
                || speedKph < 0
                || speedKph > 200) {
            return DataValidity.INVALID_SPEED;
        }

        if (gear == null) {
            return DataValidity.INCOMPLETE;
        }

        return DataValidity.VALID;
    }
}
