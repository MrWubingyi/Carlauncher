package com.example.carlauncher.data;

import com.example.carlauncher.model.VehicleState;

public interface VehicleDataSource {
    void start(Listener listener);

    void stop();

    boolean isRunning();

    interface Listener {

        /**
         * 回调完整的车辆状态快照。
         * 回调线程不保证是主线程。
         */
        void onStateChanged(VehicleState state);

        /**
         * 数据源本身的连接或可用状态发生变化。
         */
        void onSourceStatusChanged(DataSourceStatus status);

        void onError(Exception exception);
    }
}
