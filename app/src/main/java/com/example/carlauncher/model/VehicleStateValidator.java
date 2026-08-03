package com.example.carlauncher.model;

public final class VehicleStateValidator {
    private VehicleStateValidator() {
    }

    public static DataValidity validate(
            float speedKph,
            String gear
    ) {
        if (Float.isNaN(speedKph)
                || Float.isInfinite(speedKph)
                || speedKph < 0
                || speedKph > 200) {
            return DataValidity.INVALID_SPEED;
        }

        if (!isValidGear(gear)) {
            return DataValidity.INCOMPLETE;
        }

        return DataValidity.VALID;
    }

    private static boolean isValidGear(String gear) {
        return "P".equals(gear)
                || "R".equals(gear)
                || "N".equals(gear)
                || "D".equals(gear);
    }
}
