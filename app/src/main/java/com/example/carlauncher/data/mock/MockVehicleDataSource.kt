package com.example.carlauncher.data.mock

import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.Gear
import com.example.carlauncher.model.TurnSignal
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.model.VehicleStateValidator
import com.example.carlauncher.model.WarningState
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** 模拟车辆数据源，用于在没有真实车辆数据时生成模拟的车辆状态。 */
class MockVehicleDataSource
/** 测试专用构造：注入可控的 [ScheduledExecutorService]， 使 100ms 定时生成逻辑可以在测试中同步驱动。 包级可见，不对外部调用方暴露。 */
internal constructor(
    private val injectedScheduler: ScheduledExecutorService? // 测试注入的可控调度器（生产恒为 null）
) : VehicleDataSource {

    private var scheduler: ScheduledExecutorService? = null // 用于定时执行模拟任务的调度器
    private var sequence: Long = 0 // 消息序列号
    private var speed: Int = 0 // 当前模拟的车速
    private var accelerating: Boolean = true // 模拟加速或减速状态
    @Volatile private var listener: VehicleDataSource.Listener? = null
    //
    //    /**
    //     * 车辆状态监听接口。
    //     */
    //    interface Listener {
    //        /**
    //         * 当新的车辆状态产生时调用。
    //         *
    //         * @param state 车辆状态数据对象
    //         */
    //        void onVehicleState(VehicleState state);
    //    }

    override val isRunning: Boolean
        get() {
            return scheduler != null && !scheduler!!.isShutdown()
        }

    /** 生产环境默认构造：首次 start() 时创建真实单线程调度器。 */
    constructor() : this(null) {}

    /**
     * 开始生成模拟数据。
     *
     * @param listener 数据监听器，当有新状态生成时回调
     */
    override fun start(listener: VehicleDataSource.Listener?) {
        if (isRunning) {
            return
        }
        this.listener = listener
        scheduler =
            if (injectedScheduler != null) injectedScheduler
            else Executors.newSingleThreadScheduledExecutor()
        listener!!.onSourceStatusChanged(DataSourceStatus.CONNECTED)
        // 每秒执行一次速度更新和状态分发
        scheduler!!.scheduleWithFixedDelay(
            Runnable { this.generateState() },
            0,
            100,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun generateState() {
        // An in-flight scheduler callback can still enter after stop().
        var currentListener = listener
        if (currentListener == null) {
            return
        }

        // 1. 先递增序列号
        sequence++

        // --- 测试场景控制 ---
        // 总循环周期：120秒 (1200个 100ms)
        // 0-60秒 (0-600): 正常模拟
        // 60-70秒 (600-700): 非法数据测试 (数值超出范围)
        // 70-100秒 (700-1000): 静默测试 (30秒不发送数据)
        // 100-120秒 (1000-1200): 恢复正常模拟
        val testCycle = sequence % 1200

        if (testCycle >= 700 && testCycle < 1000) {
            // 静默测试：直接返回，不触发 onStateChanged
            if (testCycle == 700L) {
                currentListener!!.onSourceStatusChanged(DataSourceStatus.NO_DATA)
            }
            return
        }

        if (testCycle == 1000L) {
            currentListener!!.onSourceStatusChanged(DataSourceStatus.CONNECTED)
        }

        updateSpeed()

        var finalSpeed = speed
        var finalRpm = calculateRpm(speed)
        var finalSoc = 70
        var finalTemp = 90.0f

        // 非法数据测试场景
        if (testCycle >= 600 && testCycle < 700) {
            finalSpeed = 255 // 超出 200 限制
            finalRpm = 9999 // 超出 8000 限制
            finalSoc = 150 // 超出 100 限制
            finalTemp = -999f // 异常低迷
        }

        val rawSpeedKph = finalSpeed.toFloat()
        val gear = if (finalSpeed == 0) Gear.P else Gear.D

        val validity =
            VehicleStateValidator.validate(
                rawSpeedKph,
                gear,
            )

        // 模拟灯光、车门、安全带和告警逻辑
        val turnSignal = calculateMockTurnSignal()
        val warning = calculateMockWarning()
        val highBeam = (sequence / 50) % 2 == 0L // 每 5 秒切换一次远光灯

        // 模拟告警类状态（长周期切换，方便测试）
        val parkingBrakeOn = (sequence / 600) % 2 == 0L // 每 60 秒切换一次手刹
        val doorLocked = (sequence / 450) % 2 == 0L // 每 45 秒切换一次车门锁
        val beltWarning = (sequence / 300) % 2 == 0L // 每 30 秒切换一次安全带告警

        val state =
            VehicleState.Builder()
                .setVersion(0)
                .setSequence(sequence)
                .setTimestampMs(System.currentTimeMillis())
                .setVehSpeedKph(finalSpeed)
                .setEngRpm(finalRpm)
                .setGear(gear)
                .setSoc(finalSoc)
                .setTurnSignal(turnSignal)
                .setParkingBrake(parkingBrakeOn)
                .setWarning(warning)
                .setValidity(validity)
                .setDoorLock(doorLocked)
                .setBeltWarning(beltWarning)
                .setHeadlightsState(1) // 模拟开启近光灯
                .setHighBeamLightsState(if (highBeam) 1 else 0)
                .setEngineCoolantTemp(finalTemp)
                .setEvBatteryLevel(finalSoc.toFloat())
                .setDataStatus(
                    if (testCycle >= 600 && testCycle < 700) DataStatus.INVALID
                    else DataStatus.NORMAL
                )!!
                .build()

        currentListener = listener
        if (currentListener != null) {
            currentListener!!.onStateChanged(state)
        }
    }

    /** 停止生成模拟数据。 */
    @Synchronized
    override fun stop() {
        if (scheduler != null) {
            scheduler!!.shutdownNow()
            scheduler = null
        }

        val currentListener = listener
        listener = null

        if (currentListener != null) {
            currentListener!!.onSourceStatusChanged(DataSourceStatus.STOPPED)
        }
    }

    /** 根据序列号循环生成不同的转向灯状态进行测试 */
    private fun calculateMockTurnSignal(): TurnSignal? {
        val cycle = (sequence / 30) % 4 // 每 3 秒切换一次状态
        if (cycle == 1L) return TurnSignal.LEFT
        if (cycle == 2L) return TurnSignal.RIGHT
        if (cycle == 3L) return TurnSignal.HAZARD
        return TurnSignal.NONE
    }

    /** 根据序列号循环生成不同的告警状态进行测试 每个状态持续 30 秒，方便观察 UI 变化 */
    private fun calculateMockWarning(): WarningState? {
        val cycle = (sequence / 300) % 3 // 每 30 秒切换一次状态 (300 * 100ms)
        if (cycle == 1L) return WarningState.GENERAL_WARNING
        if (cycle == 2L) return WarningState.CRITICAL
        return WarningState.NONE
    }

    /** 内部方法：模拟速度的变化逻辑（在 0 到 100 之间往复）。 */
    private fun updateSpeed() {
        if (accelerating) {
            speed += 2

            if (speed >= 100) {
                speed = 100
                accelerating = false
            }
        } else {
            speed -= 2

            if (speed <= 0) {
                speed = 0
                accelerating = true
            }
        }
    }

    /**
     * 根据速度简单计算模拟的引擎转速。
     *
     * @param speedKph 当前车速 (km/h)
     * @return 模拟的转速 (RPM)
     */
    private fun calculateRpm(speedKph: Int): Int {
        if (speedKph == 0) {
            return 800 // 怠速转速
        }

        return 800 + speedKph * 25 // 线性增长模拟
    }
}
