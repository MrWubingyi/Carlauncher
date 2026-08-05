package com.example.carlauncher.data.mock;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
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
    private long sequence = 0; // 消息序列号
    private int speed = 0;    // 当前模拟的车速
    private boolean accelerating = true; // 模拟加速或减速状态
    private volatile Listener listener;

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
        scheduler = Executors.newSingleThreadScheduledExecutor();
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
        updateSpeed();

        float rawSpeedKph = speed;
        String gear = speed == 0 ? "P" : "D";

        DataValidity validity =
                VehicleStateValidator.validate(
                        rawSpeedKph,
                        gear
                );

        VehicleState state = new VehicleState(
                0,
                ++sequence,
                System.currentTimeMillis(),
                speed,
                calculateRpm(speed),
                gear,
                70,//电量不变
                TurnSignal.NONE,
                speed == 0 && "P".equals(gear), // 模拟：停车且档位为P时开启手刹
                WarningState.NONE,
                validity
        );

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
