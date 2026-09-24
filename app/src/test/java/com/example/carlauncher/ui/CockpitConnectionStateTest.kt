package com.example.carlauncher.ui

import org.junit.Assert.*
import org.junit.Test

/** 验证 CockpitConnectionState 的五种状态对应的 UI 属性。 */
open class CockpitConnectionStateTest {

    @Test
    open fun testOnline() {
        val state = CockpitConnectionState.ONLINE
        assertEquals("按钮文字应为 STOP RECEIVE", "STOP RECEIVE", state.actionLabel)
        assertTrue("在线状态按钮应启用", state.isActionEnabled)
        assertTrue("在线状态应为停止动作", state.isStopAction)
    }

    @Test
    open fun testConnecting() {
        val state = CockpitConnectionState.CONNECTING
        assertEquals("按钮文字应为 PLEASE WAIT", "PLEASE WAIT", state.actionLabel)
        assertFalse("连接中按钮不应启用", state.isActionEnabled)
        assertFalse("连接中不应为停止动作", state.isStopAction)
    }

    @Test
    open fun testInvalidData() {
        val state = CockpitConnectionState.INVALID_DATA
        assertEquals("按钮文字应为 STOP RECEIVE", "STOP RECEIVE", state.actionLabel)
        assertTrue("异常数据状态按钮应启用", state.isActionEnabled)
        assertTrue("异常数据状态应为停止动作", state.isStopAction)
    }

    @Test
    open fun testDisconnected() {
        val state = CockpitConnectionState.DISCONNECTED
        assertEquals("按钮文字应为 START RECEIVE", "START RECEIVE", state.actionLabel)
        assertTrue("断开状态按钮应启用", state.isActionEnabled)
        assertFalse("断开状态不应为停止动作", state.isStopAction)
    }

    @Test
    open fun testRecovering() {
        val state = CockpitConnectionState.RECOVERING
        assertEquals("按钮文字应为 PLEASE WAIT", "PLEASE WAIT", state.actionLabel)
        assertFalse("恢复中按钮不应启用", state.isActionEnabled)
        assertFalse("恢复中不应为停止动作", state.isStopAction)
    }
}
