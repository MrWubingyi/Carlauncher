package com.example.carlauncher.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * DataStatus 枚举与协议线值的映射契约。
 */
public class DataStatusTest {

    @Test
    public void getValue_matchesWireContract() {
        assertEquals(0, DataStatus.NORMAL.getValue());
        assertEquals(1, DataStatus.INVALID.getValue());
        assertEquals(2, DataStatus.NO_DATA.getValue());
        assertEquals(3, DataStatus.SOURCE_DISCONNECTED.getValue());
        assertEquals(4, DataStatus.TRANSPORT_DISCONNECTED.getValue());
    }
}
