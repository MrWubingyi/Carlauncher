package com.example.carlauncher.model

import java.util.Arrays
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/* Design section 6.2: rounding precedes range validation; speed errors precede gear errors. */
@RunWith(Parameterized::class)
open class VehicleDesignBoundaryTest(
    private val speed: Float,
    private val gear: Gear?,
    private val expected: DataValidity?,
) {

    @Test
    open fun validate_respectsRoundedBoundaryAndErrorPrecedence() {
        assertEquals(expected, VehicleStateValidator.validate(speed, gear))
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: speed={0}, gear={1}, validity={2}")
        @JvmStatic
        fun cases(): Collection<Array<Any?>?>? =
            Arrays.asList<Array<Any?>?>(
                *arrayOf<Array<Any?>?>(
                    arrayOf<Any?>(-0.5f, Gear.D, DataValidity.VALID),
                    arrayOf<Any?>(Math.nextDown(-0.5f), Gear.D, DataValidity.INVALID_SPEED),
                    arrayOf<Any?>(0f, Gear.P, DataValidity.VALID),
                    arrayOf<Any?>(200f, Gear.D, DataValidity.VALID),
                    arrayOf<Any?>(Math.nextDown(200.5f), Gear.D, DataValidity.VALID),
                    arrayOf<Any?>(200.5f, Gear.D, DataValidity.INVALID_SPEED),
                    arrayOf<Any?>(java.lang.Float.MAX_VALUE, Gear.D, DataValidity.INVALID_SPEED),
                    arrayOf<Any?>(-java.lang.Float.MAX_VALUE, Gear.D, DataValidity.INVALID_SPEED),
                    arrayOf<Any?>(java.lang.Float.NaN, null, DataValidity.INVALID_SPEED),
                    arrayOf<Any?>(
                        java.lang.Float.POSITIVE_INFINITY,
                        null,
                        DataValidity.INVALID_SPEED,
                    ),
                    arrayOf<Any?>(0f, null, DataValidity.INCOMPLETE),
                )
            )
    }
}
