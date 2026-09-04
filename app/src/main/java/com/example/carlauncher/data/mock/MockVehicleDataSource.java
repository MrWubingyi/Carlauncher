package com.example.carlauncher.data.mock;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.DataStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.Gear;
import com.example.carlauncher.model.TurnSignal;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.model.WarningState;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.model.VehicleStateValidator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 模拟车辆数据源，用于在没有真实车辆数据时生成模拟的车辆状态。
 */
public final class MockVehicleDataSource
        implements VehicleDataSource {

    private ScheduledExecutorService scheduler; // 用于定时执行模拟任务的调度器
    private ScheduledExecutorService injectedScheduler; // 测试注入的可控调度器（生产恒为 null）
    private long sequence = 0; // 消息序列号
    private int speed = 0;    // 当前模拟的车速
    private boolean accelerating = true; // 模拟加速或减速状态
    private volatile Listener listener;

    /**
     * 生产环境默认构造：首次 start() 时创建真实单线程调度器。
     */
    public MockVehicleDataSource() {
        this(null);
    }

    /**
     * 测试专用构造：注入可控的 {@link ScheduledExecutorService}，
     * 使 100ms 定时生成逻辑可以在测试中同步驱动。
     * 包级可见，不对外部调用方暴露。
     */
    MockVehicleDataSource(ScheduledExecutorService scheduler) {
        this.injectedScheduler = scheduler;
    }

    /**
     * 开始生成模拟数据。
     *
     * @param listener 数据监听器，当有新状态生成时回调
     */
    public void start(Listener listener) {
        if (isRunning()) {
            return;
        }
        this.listener = listener;
        scheduler = injectedScheduler != null
                ? injectedScheduler
                : Executors.newSingleThreadScheduledExecutor();
        listener.onSourceStatusChanged(DataSourceStatus.CONNECTED);
        // 每秒执行一次速度更新和状态分发
        scheduler.scheduleWithFixedDelay(
                this::generateState,
                0,
                100,
                TimeUnit.MILLISECONDS
        );
    }

    private void generateState() {
        // 1. 先递增序列号
        sequence++;

        // --- 测试场景控制 ---
        // 总循环周期：120秒 (1200个 100ms)
        // 0-60秒 (0-600): 正常模拟
        // 60-70秒 (600-700): 非法数据测试 (数值超出范围)
        // 70-100秒 (700-1000): 静默测试 (30秒不发送数据)
        // 100-120秒 (1000-1200): 恢复正常模拟
        long testCycle = sequence % 1200;

        if (testCycle >= 700 && testCycle < 1000) {
            // 静默测试：直接返回，不触发 onStateChanged
            if (testCycle == 700) {
                listener.onSourceStatusChanged(DataSourceStatus.NO_DATA);
            }
            return;
        }

        if (testCycle == 1000) {
            listener.onSourceStatusChanged(DataSourceStatus.CONNECTED);
        }

        updateSpeed();

        int finalSpeed = speed;
        int finalRpm = calculateRpm(speed);
        int finalSoc = 70;
        float finalTemp = 90.0f;

        // 非法数据测试场景
        if (testCycle >= 600 && testCycle < 700) {
            finalSpeed = 255;  // 超出 200 限制
            finalRpm = 9999;   // 超出 8000 限制
            finalSoc = 150;    // 超出 100 限制
            finalTemp = -999f; // 异常低迷
        }

        float rawSpeedKph = finalSpeed;
        Gear gear = finalSpeed == 0 ? Gear.P : Gear.D;

        DataValidity validity =
                VehicleStateValidator.validate(
                        rawSpeedKph,
                        gear
                );

        // 模拟灯光、车门、安全带和告警逻辑
        TurnSignal turnSignal = calculateMockTurnSignal();
        WarningState warning = calculateMockWarning();
        boolean highBeam = (sequence / 50) % 2 == 0; // 每 5 秒切换一次远光灯
        
        // 模拟告警类状态（长周期切换，方便测试）
        boolean parkingBrakeOn = (sequence / 600) % 2 == 0; // 每 60 秒切换一次手刹
        boolean doorLocked = (sequence / 450) % 2 == 0;    // 每 45 秒切换一次车门锁
        boolean beltWarning = (sequence / 300) % 2 == 0;   // 每 30 秒切换一次安全带告警

        VehicleState state = new VehicleState.Builder()
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
                .setHighBeamLightsState(highBeam ? 1 : 0)
                .setEngineCoolantTemp(finalTemp)
                .setEvBatteryLevel((float) finalSoc)
                .setDataStatus(testCycle >= 600 && testCycle < 700 ? DataStatus.INVALID : DataStatus.NORMAL)
                .build();

        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onStateChanged(state);
        }
    }

    /**
     * 停止生成模拟数据。
     */
    @Override
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        Listener currentListener = listener;
        listener = null;

        if (currentListener != null) {
            currentListener.onSourceStatusChanged(
                    DataSourceStatus.STOPPED
            );
        }
    }
    /**
     * 根据序列号循环生成不同的转向灯状态进行测试
     */
    private TurnSignal calculateMockTurnSignal() {
        long cycle = (sequence / 30) % 4; // 每 3 秒切换一次状态
        if (cycle == 1) return TurnSignal.LEFT;
        if (cycle == 2) return TurnSignal.RIGHT;
        if (cycle == 3) return TurnSignal.HAZARD;
        return TurnSignal.NONE;
    }

    /**
     * 根据序列号循环生成不同的告警状态进行测试
     * 每个状态持续 30 秒，方便观察 UI 变化
     */
    private WarningState calculateMockWarning() {
        long cycle = (sequence / 300) % 3; // 每 30 秒切换一次状态 (300 * 100ms)
        if (cycle == 1) return WarningState.GENERAL_WARNING;
        if (cycle == 2) return WarningState.CRITICAL;
        return WarningState.NONE;
    }

    /**
     * 内部方法：模拟速度的变化逻辑（在 0 到 100 之间往复）。
     */
    private void updateSpeed() {
        if (accelerating) {
            speed += 2;

            if (speed >= 100) {
                speed = 100;
                accelerating = false;
            }
        } else {
            speed -= 2;

            if (speed <= 0) {
                speed = 0;
                accelerating = true;
            }
        }
    }

    /**
     * 根据速度简单计算模拟的引擎转速。
     *
     * @param speedKph 当前车速 (km/h)
     * @return 模拟的转速 (RPM)
     */
    private int calculateRpm(int speedKph) {
        if (speedKph == 0) {
            return 800; // 怠速转速
        }

        return 800 + speedKph * 25; // 线性增长模拟
    }
//
//    /**
//     * 车辆状态监听接口。
//     */
//    public interface Listener {
//        /**
//         * 当新的车辆状态产生时调用。
//         *
//         * @param state 车辆状态数据对象
//         */
//        void onVehicleState(VehicleState state);
//    }

    @Override
    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }
}
