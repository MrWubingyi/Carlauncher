package com.example.carlauncher.model

import com.example.carlauncher.data.VehicleProtocol

class VehicleStateValidator private constructor() {
    companion object {

        @JvmStatic
        fun validate(
            speedKph: Float,
            gear: Gear?,
        ): DataValidity? {
            if (
                (java.lang.Float.isNaN(speedKph) ||
                    java.lang.Float.isInfinite(speedKph) ||
                    !VehicleProtocol.isSpeedValid(Math.round(speedKph)))
            ) {
                return DataValidity.INVALID_SPEED
            }

            if (gear == null) {
                return DataValidity.INCOMPLETE
            }

            return DataValidity.VALID
        }
    }
}
