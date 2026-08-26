package com.example.carlauncher.data.vhal;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.VehicleTurnSignal;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.VehicleProtocol;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.Gear;
import com.example.carlauncher.model.TurnSignal;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.model.VehicleStateValidator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 从 AAOS CarService 读取车辆属性并发布统一的车辆状态快照。
 * 该类作为 VHAL (车辆硬件抽象层) 的数据源实现。
 */
public final class VhalVehicleDataSource implements VehicleDataSource {
    private static final String TAG = "VEHICLE_VHAL";
    private static final int GLOBAL_AREA_ID = 0; // 全局区域 ID
    private static final long NO_DATA_TIMEOUT_MS = VehicleProtocol.DATA_STALE_TIMEOUT_MS; // 无数据超时阈值
    private static final long WATCHDOG_PERIOD_MS = 500L; // 看门狗检查周期（0.5秒）

    private final Context applicationContext;
    private final Handler carHandler = new Handler(Looper.getMainLooper());

    private Car car; // Car API 核心连接对象
    private CarPropertyManager propertyManager; // 属性管理器，用于订阅车速、档位等
    private ScheduledExecutorService watchdog; // 用于检查数据超时的定时任务
    private Listener listener; // 数据回调监听器

    private boolean running; // 数据源是否正在运行
    private int speedPropertyId; // 动态确定的车速属性 ID
    private int turnSignalPropertyId; // 动态确定的转向灯属性 ID
    private boolean speedRegistered;
    private boolean gearRegistered;
    private boolean turnSignalRegistered;
    private boolean parkingBrakeRegistered;

    private boolean headlightsStateRegistered; // 前照灯/近光灯状态
    private boolean highBeamLightsStateRegistered; //远光灯状态
    private boolean engineCoolantTempRegistered; //冷却液温度
    private boolean evBatteryLevelRegistered;//soc电池电量
    private boolean doorLockRegistered; //车门上锁状态
    private boolean beltWarningRegistered; // 安全带告警

    // 缓存的原始车辆数据
    private Float speedMps; // 车速（米/秒）
    private final VehicleState.Builder stateBuilder = new VehicleState.Builder();

    private long lastSpeedTimestampMs; // 上次收到车速的时间戳
    private long sequence; // 快照序列号
    private DataSourceStatus currentStatus = DataSourceStatus.STOPPED;

    public VhalVehicleDataSource(Context context) {
        // 使用 ApplicationContext 防止内存泄漏
        applicationContext = context.getApplicationContext();
    }

    /**
     * 车辆属性变化的回调接口实现
     */
    private final CarPropertyManager.CarPropertyEventCallback propertyCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    // 当订阅的属性发生变化时调用
                    handlePropertyChanged(value);
                }

                @Override
                public void onErrorEvent(int propertyId, int areaId) {
                    handlePropertyError(propertyId, areaId, 0);
                }

                @Override
                public void onErrorEvent(int propertyId, int areaId, int errorCode) {
                    handlePropertyError(propertyId, areaId, errorCode);
                }
            };

    @Override
    public synchronized void start(Listener listener) {
        if (running) {
            return;
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        running = true;
        this.listener = listener;
        updateStatusLocked(DataSourceStatus.CONNECTING);
        startWatchdogLocked(); // 启动超时监控
        connectCarServiceLocked(); // 连接系统 CarService
    }

    /**
     * 异步连接 Android 系统服务 CarService
     */
    private void connectCarServiceLocked() {
        try {
            car = Car.createCar(
                    applicationContext,
                    carHandler,
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                    (connectedCar, ready) -> onCarLifecycleChanged(connectedCar, ready)
            );
        } catch (RuntimeException exception) {
            reportFatalErrorLocked("创建 CarService 客户端失败", exception);
        }
    }

    /**
     * 当 CarService 连接状态发生变化时的回调
     */
    private synchronized void onCarLifecycleChanged(Car connectedCar, boolean ready) {
        if (!running) {
            return;
        }

        if (!ready) {
            // 服务断开连接，重置状态
            propertyManager = null;
            resetValuesLocked();
            updateStatusLocked(DataSourceStatus.DISCONNECTED);
            return;
        }

        try {
            // 获取属性管理器服务
            propertyManager = (CarPropertyManager) connectedCar.getCarManager(Car.PROPERTY_SERVICE);
            if (propertyManager == null) {
                reportFatalErrorLocked("无法获取 CarPropertyManager", new IllegalStateException("property service missing"));
                return;
            }

            // 成功连接后，注册需要监听的车辆属性
            registerPropertyCallbacksLocked();
        } catch (RuntimeException exception) {
            reportFatalErrorLocked("连接 CarPropertyManager 失败", exception);
        }
    }

    /**
     * 向系统注册订阅具体的车辆属性（速度、档位、转向灯等）
     */
    private void registerPropertyCallbacksLocked() {
        // 1. 订阅车速 (优先使用 Display Speed)
        speedPropertyId = chooseSpeedPropertyLocked();
        speedRegistered = registerPropertyLocked(
                speedPropertyId,
                CarPropertyManager.SENSOR_RATE_UI, // UI 更新频率
                true // 车速是核心属性，必须成功
        );

        // 2. 订阅档位
        gearRegistered = registerPropertyLocked(
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE, // 仅在变化时通知
                false
        );

        // 3. 订阅转向灯
        turnSignalPropertyId = chooseTurnSignalPropertyLocked();
        turnSignalRegistered = turnSignalPropertyId != 0
                && registerPropertyLocked(
                turnSignalPropertyId,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );

        // 4. 订阅手刹状态
        parkingBrakeRegistered = registerPropertyLocked(
                VehiclePropertyIds.PARKING_BRAKE_ON,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );
        //5.订阅前照灯/近光灯状态
        headlightsStateRegistered = registerPropertyLocked(
                VehiclePropertyIds.HEADLIGHTS_STATE,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );
        //6.订阅远光灯状态
        highBeamLightsStateRegistered = registerPropertyLocked(
                VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );
        //7.订阅冷却液温度
        engineCoolantTempRegistered = registerPropertyLocked(
                VehiclePropertyIds.ENGINE_COOLANT_TEMP,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );
        //8.订阅soc电池电量
        evBatteryLevelRegistered = registerPropertyLocked(
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );
        //9.订阅车门上锁状态
        doorLockRegistered = registerPropertyLocked(
                VehiclePropertyIds.DOOR_LOCK,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );
        //10.订阅安全带状态
        beltWarningRegistered = registerPropertyLocked(
                VehiclePropertyIds.SEAT_BELT_BUCKLED,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );

        if (!speedRegistered) {
            reportFatalErrorLocked("无法读取 VHAL 车速属性", new IllegalStateException("speed subscription failed"));
            return;
        }

        Log.i(TAG, "VHAL 订阅完成: 车速=" + speedRegistered + " 档位=" + gearRegistered
                + " 转向灯=" + turnSignalRegistered + " 手刹=" + parkingBrakeRegistered
                + " 前照灯/近光灯=" + headlightsStateRegistered
                + " 远光灯=" + highBeamLightsStateRegistered
                + " 冷却液温度=" + engineCoolantTempRegistered
                + " soc电池电量=" + evBatteryLevelRegistered
                + " 安全带告警=" + beltWarningRegistered
        );

        updateStatusLocked(DataSourceStatus.NO_DATA);
    }

    /**
     * 选择合适的车速属性 ID（不同车型支持的 ID 可能不同）
     */
    private int chooseSpeedPropertyLocked() {
        if (isPropertySupportedLocked(VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY)) {
            return VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY; // 优先使用仪表盘显示速度
        }
        return VehiclePropertyIds.PERF_VEHICLE_SPEED; // 备选：实际物理速度
    }

    /**
     * 选择合适的转向灯属性 ID
     */
    private int chooseTurnSignalPropertyLocked() {
        if (isPropertySupportedLocked(VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE)) {
            return VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE;
        }
        return 0;
    }

    /**
     * 通用的属性注册逻辑
     */
    private boolean registerPropertyLocked(int propertyId, float sampleRate, boolean required) {
        if (!isPropertySupportedLocked(propertyId)) {
            Log.w(TAG, "VHAL 不支持该属性: " + propertyId);
            return false;
        }

        try {
            propertyManager.subscribePropertyEvents(propertyId, sampleRate, propertyCallback);
            return true;
        } catch (SecurityException exception) {
            Log.w(TAG, "没有权限访问 VHAL 属性: " + propertyId, exception);
            if (required) notifyErrorLocked(exception);
            return false;
        } catch (RuntimeException exception) {
            Log.w(TAG, "VHAL 订阅失败: " + propertyId, exception);
            if (required) notifyErrorLocked(exception);
            return false;
        }
    }

    /**
     * 检查当前车辆硬件是否支持某个属性
     */
    private boolean isPropertySupportedLocked(int propertyId) {
        if (propertyManager == null) return false;
        try {
            CarPropertyConfig<?> config = propertyManager.getCarPropertyConfig(propertyId);
            return config != null;
        } catch (RuntimeException exception) {
            Log.w(TAG, "读取 VHAL 属性配置失败: " + propertyId, exception);
            return false;
        }
    }

    /**
     * 处理属性变化事件：将原始数据缓存到内存中
     */
    private synchronized void handlePropertyChanged(CarPropertyValue value) {
        if (!running || value == null) return;
        if (value.getPropertyStatus() != CarPropertyValue.STATUS_AVAILABLE) {
            Log.w(TAG, "VHAL 属性当前不可用: " + value.getPropertyId());
            return;
        }

        Object propertyValue = value.getValue();
        int propertyId = value.getPropertyId();

        switch (propertyId) {
            case VehiclePropertyIds.DOOR_LOCK:
                if (propertyValue instanceof Boolean) {
                    stateBuilder.setDoorLock((Boolean) propertyValue);
                }
                break;
            case VehiclePropertyIds.GEAR_SELECTION:
                if (propertyValue instanceof Integer) {
                    stateBuilder.setGear(mapGear((Integer) propertyValue));
                }
                break;
            case VehiclePropertyIds.PARKING_BRAKE_ON:
                if (propertyValue instanceof Boolean) {
                    stateBuilder.setParkingBrake((Boolean) propertyValue);
                }
                break;

            case VehiclePropertyIds.HEADLIGHTS_STATE:
                if (propertyValue instanceof Integer) {
                    stateBuilder.setHeadlightsState((Integer) propertyValue);
                }
                break;
            case VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE:
                if (propertyValue instanceof Integer) {
                    stateBuilder.setHighBeamLightsState((Integer) propertyValue);
                }
                break;

            case VehiclePropertyIds.ENGINE_COOLANT_TEMP:
                if (propertyValue instanceof Float) {
                    stateBuilder.setEngineCoolantTemp((Float) propertyValue);
                }
                break;

            case VehiclePropertyIds.EV_BATTERY_LEVEL:
                if (propertyValue instanceof Float) {
                    stateBuilder.setEvBatteryLevel((Float) propertyValue);
                }
                break;
            case VehiclePropertyIds.SEAT_BELT_BUCKLED:
                if (propertyValue instanceof Boolean) {
                    // VHAL 中 true 表示已扣紧，应用中 beltWarning true 表示未扣紧 (告警)
                    stateBuilder.setBeltWarning(!(Boolean) propertyValue);
                }
                break;
            default:
                if (propertyId == speedPropertyId && propertyValue instanceof Float) {
                    speedMps = (Float) propertyValue;
                    lastSpeedTimestampMs = System.currentTimeMillis(); // 更新最近收到数据的时间
                } else if (propertyId == turnSignalPropertyId && propertyValue instanceof Integer) {
                    stateBuilder.setTurnSignal(mapTurnSignal((Integer) propertyValue));
                } else {
                    return;
                }
                break;
        }

        // 每次关键属性变化后，打包发布一次快照
        publishSnapshotLocked();
    }

    /**
     * 打包当前所有车辆状态，生成 VehicleState 对象并回调给监听器
     */
    private void publishSnapshotLocked() {
        Listener currentListener = listener;
        if (!running || currentListener == null || speedMps == null) {
            return;
        }

        // 单位换算：米/秒 -> 千米/小时
        float speedKph = speedMps * 3.6f;
        int roundedSpeedKph = Math.round(speedKph);

        // 校验数据有效性
        DataValidity validity = VehicleStateValidator.validate(speedKph, stateBuilder.getGear());

        // 更新 Builder 中的动态属性
        stateBuilder
                .setVersion(1)
                .setSequence(++sequence)
                .setTimestampMs(System.currentTimeMillis())
                .setVehSpeedKph(roundedSpeedKph)
                .setEngRpm(calculateRpm(speedKph))
                .setValidity(validity)
                .setSoc(70); // 假设剩余油量/电量

        // 创建统一的状态模型
        VehicleState state = stateBuilder.build();

        updateStatusLocked(DataSourceStatus.CONNECTED);
        // 周期性打印日志，避免日志过碎
        if (sequence == 1 || sequence % 100 == 0) {
            Log.i(TAG, "快照发布 seq=" + sequence + " 速度=" + state.getVehSpeedKph() + " 档位=" + state.getGear());
        }
        currentListener.onStateChanged(state);
    }

    private synchronized void handlePropertyError(int propertyId, int areaId, int errorCode) {
        if (!running) return;
        Log.w(TAG, "VHAL 属性错误: property=" + propertyId + " code=" + errorCode);
        if (propertyId == speedPropertyId) {
            updateStatusLocked(DataSourceStatus.NO_DATA);
        }
    }

    /**
     * 启动看门狗线程，定期检查车速数据是否停止上报
     */
    private void startWatchdogLocked() {
        watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.scheduleWithFixedDelay(
                this::checkDataTimeout,
                WATCHDOG_PERIOD_MS,
                WATCHDOG_PERIOD_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 检查数据超时：如果超过 1.5 秒没收到车速，认为信号丢失
     */
    private synchronized void checkDataTimeout() {
        if (!running || lastSpeedTimestampMs == 0L) return;

        long ageMs = System.currentTimeMillis() - lastSpeedTimestampMs;
        if (ageMs > NO_DATA_TIMEOUT_MS) {
            updateStatusLocked(DataSourceStatus.NO_DATA);
        }
    }

    /**
     * 更新并通知数据源状态（连接中、已连接、无数据、错误等）
     */
    private void updateStatusLocked(DataSourceStatus status) {
        if (currentStatus == status) return;
        currentStatus = status;
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onSourceStatusChanged(status);
        }
    }

    private void notifyErrorLocked(Exception exception) {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onError(exception);
        }
    }

    private void reportFatalErrorLocked(String message, RuntimeException exception) {
        Log.e(TAG, message, exception);
        updateStatusLocked(DataSourceStatus.ERROR);
        notifyErrorLocked(exception);
    }

    private void resetValuesLocked() {
        speedMps = null;
        lastSpeedTimestampMs = 0L;
        // 注意：Builder 状态在重置时可能也需要重置部分属性，或者直接创建新 Builder
        // 这里简单处理，主要清除速度触发器
    }

    /**
     * 根据车速简单估算转速（仅用于 UI 模拟展示）
     */
    private static int calculateRpm(float speedKph) {
        if (speedKph <= 0f) return 800; // 怠速
        return Math.min(8000, 800 + Math.round(speedKph) * 25);
    }

    /**
     * 将 AAOS 档位常量映射为应用内部枚举
     */
    private static Gear mapGear(int gear) {
        switch (gear) {
            case VehicleGear.GEAR_PARK:
                return Gear.P;
            case VehicleGear.GEAR_REVERSE:
                return Gear.R;
            case VehicleGear.GEAR_NEUTRAL:
                return Gear.N;
            case VehicleGear.GEAR_DRIVE:
                return Gear.D;
            default:
                return Gear.P;
        }
    }

    /**
     * 将 AAOS 转向灯位掩码映射为应用内部枚举
     */
    private static TurnSignal mapTurnSignal(int value) {
        boolean left = (value & VehicleTurnSignal.STATE_LEFT) != 0;
        boolean right = (value & VehicleTurnSignal.STATE_RIGHT) != 0;

        if (left && right) return TurnSignal.HAZARD; // 双闪
        if (left) return TurnSignal.LEFT;
        if (right) return TurnSignal.RIGHT;
        return TurnSignal.NONE;
    }

    /**
     * 停止订阅所有车辆属性
     */
    private void unregisterCallbacksLocked() {
        if (propertyManager == null) return;
        try {
            propertyManager.unsubscribePropertyEvents(propertyCallback);
        } catch (RuntimeException exception) {
            Log.w(TAG, "注销 VHAL 回调失败", exception);
        }
        speedRegistered = false;
        gearRegistered = false;
        turnSignalRegistered = false;
        parkingBrakeRegistered = false;
        headlightsStateRegistered =false;
        highBeamLightsStateRegistered = false; //远光灯状态
        engineCoolantTempRegistered = false; //冷却液温度
        evBatteryLevelRegistered = false;//soc电池电量
        doorLockRegistered = false;
        beltWarningRegistered = false;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;

        running = false;
        unregisterCallbacksLocked();

        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }

        if (car != null) {
            car.disconnect();
            car = null;
        }
        propertyManager = null;
        resetValuesLocked();

        updateStatusLocked(DataSourceStatus.STOPPED);
        listener = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }
}
