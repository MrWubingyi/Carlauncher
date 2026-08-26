package com.example.carlauncher.data;

/**
 * 车辆协议常量定义。
 * 包含超时阈值、非法值处理及数据有效性基准。
 */
public final class VehicleProtocol {

    private VehicleProtocol() {
        // 防止实例化
    }

    // --- 超时阈值 (Timeout Thresholds) ---

    /** TCP 连接建立超时时间 (毫秒) */
    public static final int CONNECT_TIMEOUT_MS = 3000;

    /** 数据接收超时时间 (毫秒) - 超过此时间未收到有效更新视为数据丢失 */
    public static final long DATA_STALE_TIMEOUT_MS = 3000;

    /** 自动重连尝试延迟时间 (秒) */
    public static final long RECONNECT_DELAY_SECONDS = 2;

    /** 心跳包发送频率 (毫秒) */
    public static final long HEARTBEAT_INTERVAL_MS = 5000;

    // --- 非法值处理 (Illegal Value Handling) ---
    // 定义各参数的非法/无效值，通常由底层 VHAL 或传感器故障产生

    /** 无效的车速值 */
    public static final int INVALID_SPEED = -1;

    /** 无效的转速值 */
    public static final int INVALID_RPM = -1;

    /** 无效的 SOC (电池电量) 值 */
    public static final int INVALID_SOC = -1;

    /** 无效的温度值 */
    public static final float INVALID_TEMP = -999.0f;

    /** 通用信号缺失占位值 (0xFF) */
    public static final int SIGNAL_MISSING_BYTE = 0xFF;

    /** 通用信号错误占位值 (0xFE) */
    public static final int SIGNAL_ERROR_BYTE = 0xFE;

    // --- 数据有效性 (Data Validity Helpers) ---

    /**
     * 校验速度是否在合法范围内 [0, 200]
     */
    public static boolean isSpeedValid(int speed) {
        return speed >= 0 && speed <= 200;
    }

    /**
     * 校验 RPM 是否在合法范围内 [0, 20000]
     */
    public static boolean isRpmValid(int rpm) {
        return rpm >= 0 && rpm <= 20000;
    }

    /**
     * 校验 SOC 是否在合法范围内 [0, 100]
     */
    public static boolean isSocValid(int soc) {
        return soc >= 0 && soc <= 100;
    }
}
