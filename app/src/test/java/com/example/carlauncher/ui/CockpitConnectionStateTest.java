package com.example.carlauncher.ui;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 验证 CockpitConnectionState 的五种状态对应的 UI 属性。
 */
public class CockpitConnectionStateTest {

    @Test
    public void testOnline() {
        CockpitConnectionState state = CockpitConnectionState.ONLINE;
        assertEquals("按钮文字应为 STOP SEND", "STOP SEND", state.getActionLabel());
        assertTrue("在线状态按钮应启用", state.isActionEnabled());
        assertTrue("在线状态应为停止动作", state.isStopAction());
    }

    @Test
    public void testConnecting() {
        CockpitConnectionState state = CockpitConnectionState.CONNECTING;
        assertEquals("按钮文字应为 PLEASE WAIT", "PLEASE WAIT", state.getActionLabel());
        assertFalse("连接中按钮不应启用", state.isActionEnabled());
        assertFalse("连接中不应为停止动作", state.isStopAction());
    }

    @Test
    public void testInvalidData() {
        CockpitConnectionState state = CockpitConnectionState.INVALID_DATA;
        assertEquals("按钮文字应为 STOP SEND", "STOP SEND", state.getActionLabel());
        assertTrue("异常数据状态按钮应启用", state.isActionEnabled());
        assertTrue("异常数据状态应为停止动作", state.isStopAction());
    }

    @Test
    public void testDisconnected() {
        CockpitConnectionState state = CockpitConnectionState.DISCONNECTED;
        assertEquals("按钮文字应为 START SEND", "START SEND", state.getActionLabel());
        assertTrue("断开状态按钮应启用", state.isActionEnabled());
        assertFalse("断开状态不应为停止动作", state.isStopAction());
    }

    @Test
    public void testRecovering() {
        CockpitConnectionState state = CockpitConnectionState.RECOVERING;
        assertEquals("按钮文字应为 PLEASE WAIT", "PLEASE WAIT", state.getActionLabel());
        assertFalse("恢复中按钮不应启用", state.isActionEnabled());
        assertFalse("恢复中不应为停止动作", state.isStopAction());
    }
}
