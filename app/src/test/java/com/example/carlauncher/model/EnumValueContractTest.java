package com.example.carlauncher.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 模型枚举与协议线值的映射契约（这些值会被序列化到 JSON 传输）。
 */
public class EnumValueContractTest {

    @Test
    public void turnSignal_getValueMatchesWireContract() {
        assertEquals(0, TurnSignal.NONE.getValue());
        assertEquals(1, TurnSignal.LEFT.getValue());
        assertEquals(2, TurnSignal.RIGHT.getValue());
        assertEquals(3, TurnSignal.HAZARD.getValue());
    }

    @Test
    public void warningState_getValueMatchesWireContract() {
        assertEquals(0, WarningState.NONE.getValue());
        assertEquals(1, WarningState.GENERAL_WARNING.getValue());
        assertEquals(2, WarningState.CRITICAL.getValue());
    }

    @Test
    public void dataValidity_getValueMatchesWireContract() {
        assertEquals(0, DataValidity.VALID.getValue());
        assertEquals(1, DataValidity.INVALID_SPEED.getValue());
        assertEquals(2, DataValidity.INCOMPLETE.getValue());
        assertEquals(3, DataValidity.STALE.getValue());
    }
}
