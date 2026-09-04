package com.example.carlauncher.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * VehicleProtocol 常量与取值范围校验的边界测试。
 */
public class VehicleProtocolTest {

    @Test
    public void isSpeedValid_boundaries() {
        assertFalse("-1 应为非法车速", VehicleProtocol.isSpeedValid(-1));
        assertTrue("0 应为合法车速下界", VehicleProtocol.isSpeedValid(0));
        assertTrue("200 应为合法车速上界", VehicleProtocol.isSpeedValid(200));
        assertFalse("201 应为非法车速", VehicleProtocol.isSpeedValid(201));
        assertTrue("100 应为合法车速", VehicleProtocol.isSpeedValid(100));
    }

    @Test
    public void isRpmValid_boundaries() {
        assertFalse("-1 应为非法转速", VehicleProtocol.isRpmValid(-1));
        assertTrue("0 应为合法转速下界", VehicleProtocol.isRpmValid(0));
        assertTrue("20000 应为合法转速上界", VehicleProtocol.isRpmValid(20000));
        assertFalse("20001 应为非法转速", VehicleProtocol.isRpmValid(20001));
    }

    @Test
    public void isSocValid_boundaries() {
        assertFalse("-1 应为非法电量", VehicleProtocol.isSocValid(-1));
        assertTrue("0 应为合法电量下界", VehicleProtocol.isSocValid(0));
        assertTrue("100 应为合法电量上界", VehicleProtocol.isSocValid(100));
        assertFalse("101 应为非法电量", VehicleProtocol.isSocValid(101));
    }

    @Test
    public void constants_matchProtocolContract() {
        assertEquals(3000, VehicleProtocol.CONNECT_TIMEOUT_MS);
        assertEquals(3000L, VehicleProtocol.DATA_STALE_TIMEOUT_MS);
        assertEquals(2L, VehicleProtocol.RECONNECT_DELAY_SECONDS);
        assertEquals(5000L, VehicleProtocol.HEARTBEAT_INTERVAL_MS);
        assertEquals(-1, VehicleProtocol.INVALID_SPEED);
        assertEquals(-1, VehicleProtocol.INVALID_RPM);
        assertEquals(-1, VehicleProtocol.INVALID_SOC);
        assertEquals(-999.0f, VehicleProtocol.INVALID_TEMP, 0.0f);
        assertEquals(0xFF, VehicleProtocol.SIGNAL_MISSING_BYTE);
        assertEquals(0xFE, VehicleProtocol.SIGNAL_ERROR_BYTE);
    }
}
