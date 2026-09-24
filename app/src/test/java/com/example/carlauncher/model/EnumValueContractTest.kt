package com.example.carlauncher.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 模型枚举与协议线值的映射契约（这些值会被序列化到 JSON 传输）。 */
open class EnumValueContractTest {

    @Test
    open fun turnSignal_getValueMatchesWireContract() {
        assertEquals(0, TurnSignal.NONE.value.toLong())
        assertEquals(1, TurnSignal.LEFT.value.toLong())
        assertEquals(2, TurnSignal.RIGHT.value.toLong())
        assertEquals(3, TurnSignal.HAZARD.value.toLong())
    }

    @Test
    open fun warningState_getValueMatchesWireContract() {
        assertEquals(0, WarningState.NONE.value.toLong())
        assertEquals(1, WarningState.GENERAL_WARNING.value.toLong())
        assertEquals(2, WarningState.CRITICAL.value.toLong())
    }

    @Test
    open fun dataValidity_getValueMatchesWireContract() {
        assertEquals(0, DataValidity.VALID.value.toLong())
        assertEquals(1, DataValidity.INVALID_SPEED.value.toLong())
        assertEquals(2, DataValidity.INCOMPLETE.value.toLong())
        assertEquals(3, DataValidity.STALE.value.toLong())
    }
}
