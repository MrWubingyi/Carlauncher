package com.example.carlauncher.data;

import com.example.carlauncher.model.VehicleState;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 模拟车辆数据源，用于在没有真实车辆数据时生成模拟的车辆状态。
 */
public class MockVehicleDataSource {

    private ScheduledExecutorService scheduler; // 用于定时执行模拟任务的调度器

    private long sequence = 0; // 消息序列号
    private int speed = 0;    // 当前模拟的车速
    private boolean accelerating = true; // 模拟加速或减速状态

    /**
     * 开始生成模拟数据。
     *
     * @param listener 数据监听器，当有新状态生成时回调
     */
    public void start(Listener listener) {
        if (isRunning()) {
            return;
        }
        sequence = 0;
        speed = 0;
        accelerating = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // 每秒执行一次速度更新和状态分发
        scheduler.scheduleWithFixedDelay(
                () -> {
                    updateSpeed(); // 更新当前速度

                    // 创建新的车辆状态对象
                    VehicleState state = new VehicleState(
                            1,
                            ++sequence,
                            System.currentTimeMillis(),
                            speed,
                            calculateRpm(speed),
                            speed == 0 ? "P" : "D",
                            79 // 固定电池电量 (SOC)
                    );

                    listener.onVehicleState(state);
                },
                0,
                100,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 停止生成模拟数据。
     */
    public void stop() {
        if (scheduler == null) {
            return;
        }

        scheduler.shutdownNow();
        scheduler = null;
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

    /**
     * 车辆状态监听接口。
     */
    public interface Listener {
        /**
         * 当新的车辆状态产生时调用。
         *
         * @param state 车辆状态数据对象
         */
        void onVehicleState(VehicleState state);
    }

    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }
}
