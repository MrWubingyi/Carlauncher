package com.example.carlauncher.data.vhal

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.VehicleTurnSignal
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.data.VehicleProtocol
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.Gear
import com.example.carlauncher.model.TurnSignal
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.model.VehicleStateValidator
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** 从 AAOS CarService 读取车辆属性并发布统一的车辆状态快照。 该类作为 VHAL (车辆硬件抽象层) 的数据源实现。 */
class VhalVehicleDataSource(context: Context?) : VehicleDataSource {

    private val applicationContext: Context?
    private val carHandler: Handler = Handler(Looper.getMainLooper())

    private var car: Car? = null // Car API 核心连接对象
    private var propertyManager: CarPropertyManager? = null // 属性管理器，用于订阅车速、档位等
    private var watchdog: ScheduledExecutorService? = null // 用于检查数据超时的定时任务
    private var listener: VehicleDataSource.Listener? = null // 数据回调监听器

    @get:Synchronized
    override var isRunning: Boolean = false
        private set // 数据源是否正在运行

    private var speedPropertyId: Int = 0 // 动态确定的车速属性 ID
    private var turnSignalPropertyId: Int = 0 // 动态确定的转向灯属性 ID
    private var speedRegistered: Boolean = false
    private var gearRegistered: Boolean = false
    private var turnSignalRegistered: Boolean = false
    private var parkingBrakeRegistered: Boolean = false

    private var headlightsStateRegistered: Boolean = false // 前照灯/近光灯状态
    private var highBeamLightsStateRegistered: Boolean = false // 远光灯状态
    private var engineCoolantTempRegistered: Boolean = false // 冷却液温度
    private var evBatteryLevelRegistered: Boolean = false // soc电池电量
    private var doorLockRegistered: Boolean = false // 车门上锁状态
    private var beltWarningRegistered: Boolean = false // 安全带告警

    // 缓存的原始车辆数据
    private var speedMps: Float? = null // 车速（米/秒）
    private val stateBuilder: VehicleState.Builder = VehicleState.Builder()

    private var lastSpeedTimestampMs: Long = 0 // 上次收到车速的时间戳
    private var sequence: Long = 0 // 快照序列号
    private var currentStatus: DataSourceStatus? = DataSourceStatus.STOPPED

    /** 车辆属性变化的回调接口实现 */
    private val propertyCallback: CarPropertyManager.CarPropertyEventCallback =
        object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                // 当订阅的属性发生变化时调用
                handlePropertyChanged(value)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                handlePropertyError(propertyId, areaId, 0)
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int, errorCode: Int) {
                handlePropertyError(propertyId, areaId, errorCode)
            }
        }

    init {
        // 使用 ApplicationContext 防止内存泄漏
        applicationContext = context!!.getApplicationContext()
    }

    @Synchronized
    override fun start(listener: VehicleDataSource.Listener?) {
        if (isRunning) {
            return
        }
        if (listener == null) {
            throw IllegalArgumentException("listener must not be null")
        }

        isRunning = true
        this.listener = listener
        updateStatusLocked(DataSourceStatus.CONNECTING)
        startWatchdogLocked() // 启动超时监控
        connectCarServiceLocked() // 连接系统 CarService
    }

    /** 异步连接 Android 系统服务 CarService */
    private fun connectCarServiceLocked() {
        try {
            car =
                Car.createCar(applicationContext, carHandler, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) {
                    connectedCar,
                    ready ->
                    onCarLifecycleChanged(connectedCar, ready)
                }
        } catch (exception: RuntimeException) {
            reportFatalErrorLocked("创建 CarService 客户端失败", exception)
        }
    }

    /** 当 CarService 连接状态发生变化时的回调 */
    @Synchronized
    private fun onCarLifecycleChanged(connectedCar: Car?, ready: Boolean) {
        if (!isRunning) {
            return
        }

        if (!ready) {
            // 服务断开连接，重置状态
            propertyManager = null
            resetValuesLocked()
            updateStatusLocked(DataSourceStatus.DISCONNECTED)
            return
        }

        try {
            // 获取属性管理器服务
            propertyManager =
                connectedCar!!.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager?
            if (propertyManager == null) {
                reportFatalErrorLocked(
                    "无法获取 CarPropertyManager",
                    IllegalStateException("property service missing"),
                )
                return
            }

            // 成功连接后，注册需要监听的车辆属性
            registerPropertyCallbacksLocked()
        } catch (exception: RuntimeException) {
            reportFatalErrorLocked("连接 CarPropertyManager 失败", exception)
        }
    }

    /** 向系统注册订阅具体的车辆属性（速度、档位、转向灯等） */
    private fun registerPropertyCallbacksLocked() {
        // 1. 订阅车速 (优先使用 Display Speed)
        speedPropertyId = chooseSpeedPropertyLocked()
        speedRegistered =
            registerPropertyLocked(
                speedPropertyId,
                CarPropertyManager.SENSOR_RATE_UI, // UI 更新频率
                true, // 车速是核心属性，必须成功
            )

        // 2. 订阅档位
        gearRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE, // 仅在变化时通知
                false,
            )

        // 3. 订阅转向灯
        turnSignalPropertyId = chooseTurnSignalPropertyLocked()
        turnSignalRegistered =
            (turnSignalPropertyId != 0 &&
                registerPropertyLocked(
                    turnSignalPropertyId,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                    false,
                ))

        // 4. 订阅手刹状态
        parkingBrakeRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.PARKING_BRAKE_ON,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false,
            )
        // 5.订阅前照灯/近光灯状态
        headlightsStateRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.HEADLIGHTS_STATE,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false,
            )
        // 6.订阅远光灯状态
        highBeamLightsStateRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false,
            )
        // 7.订阅冷却液温度
        engineCoolantTempRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.ENGINE_COOLANT_TEMP,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false,
            )
        // 8.订阅soc电池电量
        evBatteryLevelRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false,
            )
        // 9.订阅车门上锁状态
        doorLockRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.DOOR_LOCK,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false,
            )
        // 10.订阅安全带状态
        beltWarningRegistered =
            registerPropertyLocked(
                VehiclePropertyIds.SEAT_BELT_BUCKLED,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false,
            )

        if (!speedRegistered) {
            reportFatalErrorLocked(
                "无法读取 VHAL 车速属性",
                IllegalStateException("speed subscription failed"),
            )
            return
        }

        Log.i(
            TAG,
            ("VHAL 订阅完成: 车速=" +
                speedRegistered +
                " 档位=" +
                gearRegistered +
                " 转向灯=" +
                turnSignalRegistered +
                " 手刹=" +
                parkingBrakeRegistered +
                " 前照灯/近光灯=" +
                headlightsStateRegistered +
                " 远光灯=" +
                highBeamLightsStateRegistered +
                " 冷却液温度=" +
                engineCoolantTempRegistered +
                " soc电池电量=" +
                evBatteryLevelRegistered +
                " 安全带告警=" +
                beltWarningRegistered),
        )

        updateStatusLocked(DataSourceStatus.NO_DATA)
    }

    /** 选择合适的车速属性 ID（不同车型支持的 ID 可能不同） */
    private fun chooseSpeedPropertyLocked(): Int {
        if (isPropertySupportedLocked(VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY)) {
            return VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY // 优先使用仪表盘显示速度
        }
        return VehiclePropertyIds.PERF_VEHICLE_SPEED // 备选：实际物理速度
    }

    /** 选择合适的转向灯属性 ID */
    private fun chooseTurnSignalPropertyLocked(): Int {
        if (isPropertySupportedLocked(VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE)) {
            return VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE
        }
        return 0
    }

    /** 通用的属性注册逻辑 */
    private fun registerPropertyLocked(
        propertyId: Int,
        sampleRate: Float,
        required: Boolean,
    ): Boolean {
        if (!isPropertySupportedLocked(propertyId)) {
            Log.w(TAG, "VHAL 不支持该属性: " + propertyId)
            return false
        }

        try {
            propertyManager!!.subscribePropertyEvents(propertyId, sampleRate, propertyCallback)
            return true
        } catch (exception: SecurityException) {
            Log.w(TAG, "没有权限访问 VHAL 属性: " + propertyId, exception)
            if (required) notifyErrorLocked(exception)
            return false
        } catch (exception: RuntimeException) {
            Log.w(TAG, "VHAL 订阅失败: " + propertyId, exception)
            if (required) notifyErrorLocked(exception)
            return false
        }
    }

    /** 检查当前车辆硬件是否支持某个属性 */
    private fun isPropertySupportedLocked(propertyId: Int): Boolean {
        if (propertyManager == null) return false
        try {
            val config = propertyManager!!.getCarPropertyConfig(propertyId)
            return config != null
        } catch (exception: RuntimeException) {
            Log.w(TAG, "读取 VHAL 属性配置失败: " + propertyId, exception)
            return false
        }
    }

    /** 处理属性变化事件：将原始数据缓存到内存中 */
    @Synchronized
    private fun handlePropertyChanged(value: CarPropertyValue<*>?) {
        if (!isRunning || value == null) return
        if (value!!.getPropertyStatus() != CarPropertyValue.STATUS_AVAILABLE) {
            Log.w(TAG, "VHAL 属性当前不可用: " + value!!.getPropertyId())
            return
        }

        val propertyValue = value!!.getValue()
        val propertyId = value!!.getPropertyId()

        when (propertyId) {
            VehiclePropertyIds.DOOR_LOCK ->
                if (propertyValue is Boolean) {
                    stateBuilder.setDoorLock(propertyValue)
                }
            VehiclePropertyIds.GEAR_SELECTION ->
                if (propertyValue is Int) {
                    stateBuilder.setGear(mapGear((propertyValue)))
                }
            VehiclePropertyIds.PARKING_BRAKE_ON ->
                if (propertyValue is Boolean) {
                    stateBuilder.setParkingBrake((propertyValue))
                }

            VehiclePropertyIds.HEADLIGHTS_STATE ->
                if (propertyValue is Int) {
                    stateBuilder.setHeadlightsState(propertyValue)
                }
            VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE ->
                if (propertyValue is Int) {
                    stateBuilder.setHighBeamLightsState(propertyValue)
                }

            VehiclePropertyIds.ENGINE_COOLANT_TEMP ->
                if (propertyValue is Float) {
                    stateBuilder.setEngineCoolantTemp(propertyValue)
                }

            VehiclePropertyIds.EV_BATTERY_LEVEL ->
                if (propertyValue is Float) {
                    stateBuilder.setEvBatteryLevel(propertyValue)
                }
            VehiclePropertyIds.SEAT_BELT_BUCKLED ->
                if (propertyValue is Boolean) {
                    // VHAL 中 true 表示已扣紧，应用中 beltWarning true 表示未扣紧 (告警)
                    stateBuilder.setBeltWarning(!propertyValue)
                }
            else ->
                if (propertyId == speedPropertyId && propertyValue is Float) {
                    speedMps = propertyValue
                    lastSpeedTimestampMs = System.currentTimeMillis() // 更新最近收到数据的时间
                } else if (propertyId == turnSignalPropertyId && propertyValue is Int) {
                    stateBuilder.setTurnSignal(mapTurnSignal((propertyValue)))
                } else {
                    return
                }
        }

        // 每次关键属性变化后，打包发布一次快照
        publishSnapshotLocked()
    }

    /** 打包当前所有车辆状态，生成 VehicleState 对象并回调给监听器 */
    private fun publishSnapshotLocked() {
        val currentListener = listener
        if (!isRunning || currentListener == null || speedMps == null) {
            return
        }

        // 单位换算：米/秒 -> 千米/小时
        val speedKph = speedMps!! * 3.6f
        val roundedSpeedKph = Math.round(speedKph)

        // 校验数据有效性
        val validity = VehicleStateValidator.validate(speedKph, stateBuilder.getGear())

        // 更新 Builder 中的动态属性
        stateBuilder
            .setVersion(1)
            .setSequence(++sequence)
            .setTimestampMs(System.currentTimeMillis())
            .setVehSpeedKph(roundedSpeedKph)
            .setEngRpm(calculateRpm(speedKph))
            .setValidity(validity)
            .setSoc(70) // 假设剩余油量/电量

        // 创建统一的状态模型
        val state = stateBuilder.build()

        updateStatusLocked(DataSourceStatus.CONNECTED)
        // 周期性打印日志，避免日志过碎
        if (sequence == 1L || sequence % 100 == 0L) {
            Log.i(
                TAG,
                "快照发布 seq=" + sequence + " 速度=" + state!!.vehSpeedKph + " 档位=" + state!!.gear,
            )
        }
        currentListener!!.onStateChanged(state)
    }

    @Synchronized
    private fun handlePropertyError(propertyId: Int, areaId: Int, errorCode: Int) {
        if (!isRunning) return
        Log.w(TAG, "VHAL 属性错误: property=" + propertyId + " code=" + errorCode)
        if (propertyId == speedPropertyId) {
            updateStatusLocked(DataSourceStatus.NO_DATA)
        }
    }

    /** 启动看门狗线程，定期检查车速数据是否停止上报 */
    private fun startWatchdogLocked() {
        watchdog = Executors.newSingleThreadScheduledExecutor()
        watchdog!!.scheduleWithFixedDelay(
            Runnable { this.checkDataTimeout() },
            WATCHDOG_PERIOD_MS,
            WATCHDOG_PERIOD_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    /** 检查数据超时：如果超过 1.5 秒没收到车速，认为信号丢失 */
    @Synchronized
    private fun checkDataTimeout() {
        if (!isRunning || lastSpeedTimestampMs == 0L) return

        val ageMs = System.currentTimeMillis() - lastSpeedTimestampMs
        if (ageMs > NO_DATA_TIMEOUT_MS) {
            updateStatusLocked(DataSourceStatus.NO_DATA)
        }
    }

    /** 更新并通知数据源状态（连接中、已连接、无数据、错误等） */
    private fun updateStatusLocked(status: DataSourceStatus?) {
        if (currentStatus == status) return
        currentStatus = status
        val currentListener = listener
        if (currentListener != null) {
            currentListener!!.onSourceStatusChanged(status)
        }
    }

    private fun notifyErrorLocked(exception: Exception?) {
        val currentListener = listener
        if (currentListener != null) {
            currentListener!!.onError(exception)
        }
    }

    private fun reportFatalErrorLocked(message: String?, exception: RuntimeException?) {
        Log.e(TAG, message, exception)
        updateStatusLocked(DataSourceStatus.ERROR)
        notifyErrorLocked(exception)
    }

    private fun resetValuesLocked() {
        speedMps = null
        lastSpeedTimestampMs = 0L
        // 注意：Builder 状态在重置时可能也需要重置部分属性，或者直接创建新 Builder
        // 这里简单处理，主要清除速度触发器
    }

    /** 停止订阅所有车辆属性 */
    private fun unregisterCallbacksLocked() {
        if (propertyManager == null) return
        try {
            propertyManager!!.unsubscribePropertyEvents(propertyCallback)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "注销 VHAL 回调失败", exception)
        }

        speedRegistered = false
        gearRegistered = false
        turnSignalRegistered = false
        parkingBrakeRegistered = false
        headlightsStateRegistered = false
        highBeamLightsStateRegistered = false // 远光灯状态
        engineCoolantTempRegistered = false // 冷却液温度
        evBatteryLevelRegistered = false // soc电池电量
        doorLockRegistered = false
        beltWarningRegistered = false
    }

    @Synchronized
    override fun stop() {
        if (!isRunning) return

        isRunning = false
        unregisterCallbacksLocked()

        if (watchdog != null) {
            watchdog!!.shutdownNow()
            watchdog = null
        }

        if (car != null) {
            car!!.disconnect()
            car = null
        }
        propertyManager = null
        resetValuesLocked()

        updateStatusLocked(DataSourceStatus.STOPPED)
        listener = null
    }

    companion object {
        private const val TAG: String = "VEHICLE_VHAL"
        private const val GLOBAL_AREA_ID: Int = 0 // 全局区域 ID
        private val NO_DATA_TIMEOUT_MS: Long = VehicleProtocol.DATA_STALE_TIMEOUT_MS // 无数据超时阈值
        private const val WATCHDOG_PERIOD_MS: Long = 500L // 看门狗检查周期（0.5秒）

        /** 根据车速简单估算转速（仅用于 UI 模拟展示） */
        @JvmStatic
        private fun calculateRpm(speedKph: Float): Int {
            if (speedKph <= 0f) return 800 // 怠速
            return Math.min(8000, 800 + Math.round(speedKph) * 25)
        }

        /** 将 AAOS 档位常量映射为应用内部枚举 */
        @JvmStatic
        private fun mapGear(gear: Int): Gear? {
            when (gear) {
                VehicleGear.GEAR_PARK -> return Gear.P
                VehicleGear.GEAR_REVERSE -> return Gear.R
                VehicleGear.GEAR_NEUTRAL -> return Gear.N
                VehicleGear.GEAR_DRIVE -> return Gear.D
                else -> return Gear.P
            }
        }

        /** 将 AAOS 转向灯位掩码映射为应用内部枚举 */
        @JvmStatic
        private fun mapTurnSignal(value: Int): TurnSignal? {
            val left = (value and VehicleTurnSignal.STATE_LEFT) != 0
            val right = (value and VehicleTurnSignal.STATE_RIGHT) != 0

            if (left && right) return TurnSignal.HAZARD // 双闪
            if (left) return TurnSignal.LEFT
            if (right) return TurnSignal.RIGHT
            return TurnSignal.NONE
        }
    }
}
