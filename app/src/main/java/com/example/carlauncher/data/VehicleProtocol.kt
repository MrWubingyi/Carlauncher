package com.example.carlauncher.data

/** 车辆协议常量定义。 包含超时阈值、非法值处理及数据有效性基准。 */
class VehicleProtocol private constructor() // 防止实例化
{
    companion object {

        // --- 超时阈值 (Timeout Thresholds) ---

        /* TCP 连接建立超时时间 (毫秒) */
        const val CONNECT_TIMEOUT_MS: Int = 3000

        /* 数据接收超时时间 (毫秒) - 超过此时间未收到有效更新视为数据丢失 */
        const val DATA_STALE_TIMEOUT_MS: Long = 3000

        /* 自动重连尝试延迟时间 (秒) */
        const val RECONNECT_DELAY_SECONDS: Long = 2

        /* 心跳包发送频率 (毫秒) */
        const val HEARTBEAT_INTERVAL_MS: Long = 5000

        // --- 非法值处理 (Illegal Value Handling) ---
        // 定义各参数的非法/无效值，通常由底层 VHAL 或传感器故障产生

        /* 无效的车速值 */
        const val INVALID_SPEED: Int = -1

        /* 无效的转速值 */
        const val INVALID_RPM: Int = -1

        /* 无效的 SOC (电池电量) 值 */
        const val INVALID_SOC: Int = -1

        /* 无效的温度值 */
        const val INVALID_TEMP: Float = -999.0f

        /* 通用信号缺失占位值 (0xFF) */
        const val SIGNAL_MISSING_BYTE: Int = 0xFF

        /* 通用信号错误占位值 (0xFE) */
        const val SIGNAL_ERROR_BYTE: Int = 0xFE

        // --- 数据有效性 (Data Validity Helpers) ---

        /** 校验速度是否在合法范围内 [0, 200] */
        @JvmStatic fun isSpeedValid(speed: Int): Boolean = speed >= 0 && speed <= 200

        /** 校验 RPM 是否在合法范围内 [0, 20000] */
        @JvmStatic fun isRpmValid(rpm: Int): Boolean = rpm >= 0 && rpm <= 20000

        /** 校验 SOC 是否在合法范围内 [0, 100] */
        @JvmStatic fun isSocValid(soc: Int): Boolean = soc >= 0 && soc <= 100
    }
}
