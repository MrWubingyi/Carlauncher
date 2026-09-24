package com.example.carlauncher.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** VehicleStateValidator 的数据有效性判定与错误优先级测试。 */
open class VehicleStateValidatorTest {

    @Test
    open fun validate_nanReturnsInvalidSpeed() {
        assertEquals(
            DataValidity.INVALID_SPEED,
            VehicleStateValidator.validate(java.lang.Float.NaN, Gear.D),
        )
    }

    @Test
    open fun validate_positiveInfinityReturnsInvalidSpeed() {
        assertEquals(
            DataValidity.INVALID_SPEED,
            VehicleStateValidator.validate(java.lang.Float.POSITIVE_INFINITY, Gear.D),
        )
    }

    @Test
    open fun validate_negativeInfinityReturnsInvalidSpeed() {
        assertEquals(
            DataValidity.INVALID_SPEED,
            VehicleStateValidator.validate(java.lang.Float.NEGATIVE_INFINITY, Gear.D),
        )
    }

    @Test
    open fun validate_negativeSpeedReturnsInvalidSpeed() {
        assertEquals(
            DataValidity.INVALID_SPEED,
            VehicleStateValidator.validate(-1.0f, Gear.D),
        )
    }

    @Test
    open fun validate_speedAboveMaxReturnsInvalidSpeed() {
        assertEquals(
            DataValidity.INVALID_SPEED,
            VehicleStateValidator.validate(201.0f, Gear.D),
        )
    }

    @Test
    open fun validate_roundingAtUpperBoundary() {
        // 200.4 四舍五入为 200，仍在合法范围
        assertEquals(
            DataValidity.VALID,
            VehicleStateValidator.validate(200.4f, Gear.D),
        )
        // 200.6 四舍五入为 201，超出合法范围
        assertEquals(
            DataValidity.INVALID_SPEED,
            VehicleStateValidator.validate(200.6f, Gear.D),
        )
    }

    @Test
    open fun validate_nullGearReturnsIncomplete() {
        assertEquals(
            DataValidity.INCOMPLETE,
            VehicleStateValidator.validate(50.0f, null),
        )
    }

    @Test
    open fun validate_validSpeedAndGearReturnsValid() {
        assertEquals(
            DataValidity.VALID,
            VehicleStateValidator.validate(50.0f, Gear.D),
        )
        assertEquals(
            DataValidity.VALID,
            VehicleStateValidator.validate(0.0f, Gear.P),
        )
    }

    @Test
    open fun validate_invalidSpeedTakesPrecedenceOverMissingGear() {
        // 非法速度应优先于缺失档位返回 INVALID_SPEED，而非 INCOMPLETE
        assertEquals(
            DataValidity.INVALID_SPEED,
            VehicleStateValidator.validate(999.0f, null),
        )
    }
}
