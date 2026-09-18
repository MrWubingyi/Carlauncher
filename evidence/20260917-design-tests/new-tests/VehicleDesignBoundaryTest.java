package com.example.carlauncher.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.assertEquals;

/** Design section 6.2: rounding precedes range validation; speed errors precede gear errors. */
@RunWith(Parameterized.class)
public class VehicleDesignBoundaryTest {
    @Parameterized.Parameters(name = "{index}: speed={0}, gear={1}, validity={2}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][] {
                {-0.5f, Gear.D, DataValidity.VALID},
                {Math.nextDown(-0.5f), Gear.D, DataValidity.INVALID_SPEED},
                {0f, Gear.P, DataValidity.VALID},
                {200f, Gear.D, DataValidity.VALID},
                {Math.nextDown(200.5f), Gear.D, DataValidity.VALID},
                {200.5f, Gear.D, DataValidity.INVALID_SPEED},
                {Float.MAX_VALUE, Gear.D, DataValidity.INVALID_SPEED},
                {-Float.MAX_VALUE, Gear.D, DataValidity.INVALID_SPEED},
                {Float.NaN, null, DataValidity.INVALID_SPEED},
                {Float.POSITIVE_INFINITY, null, DataValidity.INVALID_SPEED},
                {0f, null, DataValidity.INCOMPLETE}
        });
    }

    private final float speed;
    private final Gear gear;
    private final DataValidity expected;

    public VehicleDesignBoundaryTest(float speed, Gear gear, DataValidity expected) {
        this.speed = speed;
        this.gear = gear;
        this.expected = expected;
    }

    @Test public void validate_respectsRoundedBoundaryAndErrorPrecedence() {
        assertEquals(expected, VehicleStateValidator.validate(speed, gear));
    }
}
