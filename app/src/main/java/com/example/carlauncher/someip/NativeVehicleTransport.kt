package com.example.carlauncher.someip

import com.example.carlauncher.model.VehicleState

/** 统一的 Native 车辆传输层接口。 屏蔽底层的 JNI 细节，仅暴露 start, stop, isAvailable 与 Listener 设置。 */
interface NativeVehicleTransport {

    /**
     * 查询 Native 传输层当前是否可用。
     *
     * @return true 表示服务正常可用，false 表示不可用
     */
    val isAvailable: Boolean

    /** 传输层回调监听接口。 */
    interface Listener {
        /**
         * 服务可用性发生变化。
         *
         * @param available true 表示服务可用，false 表示不可用
         */
        fun onAvailabilityChanged(available: Boolean)

        /**
         * 接收到完整的车辆状态快照。
         *
         * @param state 解码并校验后的车辆状态对象
         */
        fun onVehicleState(state: VehicleState?)

        /**
         * 传输层或协议解析产生错误。
         *
         * @param throwable 异常详情
         */
        fun onError(throwable: Throwable?)
    }

    /**
     * 启动 Native 传输层。
     *
     * @return true 表示启动成功或已启动，false 表示启动失败
     */
    fun start(): Boolean

    /** 停止 Native 传输层。 */
    fun stop()

    /**
     * 设置传输层监听器。
     *
     * @param listener 监听器实例，为 null 时清除监听
     */
    fun setListener(listener: Listener?)
}
