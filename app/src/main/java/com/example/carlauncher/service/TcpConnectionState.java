package com.example.carlauncher.service;

/**
 * 车辆连接及数据同步状态。
 */
public enum TcpConnectionState {
    /** 已建立连接，且数据同步正常 */
    ONLINE,
    /** 连接已断开 */
    DISCONNECTED,
    /** 正在尝试重新连接或恢复同步 */
    RECOVERING,
    /** 握手或鉴权中 */
    CONNECTING
}
