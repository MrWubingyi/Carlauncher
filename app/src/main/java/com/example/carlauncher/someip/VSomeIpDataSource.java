package com.example.carlauncher.someip;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.DataStatus;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.model.VehicleState;

/**
 * 持有 NativeVehicleTransport 的 VehicleDataSource 实现。
 * 负责接收传输层解包后的 Event 数据与可用性变化，
 * 更新连接监视器（SomeipConnectionMonitor），并推送到 Service / Repository 层。
 */
public class VSomeIpDataSource implements VehicleDataSource {
    private static final String TAG = "VSOMEIP_DATA_SOURCE";

    private final NativeVehicleTransport transport;
    private final SomeipConnectionMonitor connectionMonitor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private VehicleDataSource.Listener listener;
    private volatile boolean running;
    private volatile VehicleState latestState;
    private volatile DataSourceStatus status = DataSourceStatus.STOPPED;

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (connectionMonitor.checkTimeout(SystemClock.elapsedRealtime())) {
                latestState = null;
                status = DataSourceStatus.NO_DATA;
                Log.w(TAG, "SOMEIP_STATE EVENT_TIMEOUT (3000 ms without vehicle event)");
                if (listener != null) {
                    listener.onSourceStatusChanged(status);
                }
            }
            mainHandler.postDelayed(this, 100);
        }
    };

    public VSomeIpDataSource(NativeVehicleTransport transport, SomeipConnectionMonitor connectionMonitor) {
        this.transport = transport;
        this.connectionMonitor = connectionMonitor;
    }

    public NativeVehicleTransport getTransport() {
        return transport;
    }

    public SomeipConnectionMonitor getConnectionMonitor() {
        return connectionMonitor;
    }

    public VehicleState getLatestState() {
        return latestState;
    }

    public DataSourceStatus getStatus() {
        return status;
    }

    @Override
    public synchronized void start(VehicleDataSource.Listener listener) {
        if (running) {
            return;
        }
        this.listener = listener;
        this.running = true;
        this.status = DataSourceStatus.CONNECTING;

        connectionMonitor.start();
        mainHandler.post(watchdogRunnable);

        if (listener != null) {
            listener.onSourceStatusChanged(status);
        }

        transport.setListener(new NativeVehicleTransport.Listener() {
            @Override
            public void onAvailabilityChanged(boolean available) {
                if (!running) return;
                long now = SystemClock.elapsedRealtime();
                connectionMonitor.onAvailability(available, now);
                if (!available) {
                    latestState = null;
                    status = DataSourceStatus.DISCONNECTED;
                } else if (latestState == null) {
                    status = DataSourceStatus.CONNECTING;
                }
                if (listener != null) {
                    listener.onSourceStatusChanged(status);
                }
            }

            @Override
            public void onVehicleState(VehicleState state) {
                if (!running || !connectionMonitor.snapshot().isAvailable()) return;
                latestState = state;
                connectionMonitor.onEvent(SystemClock.elapsedRealtime());
                status = state.getDataStatus() == DataStatus.NO_DATA
                        ? DataSourceStatus.NO_DATA : DataSourceStatus.CONNECTED;
                if (listener != null) {
                    listener.onStateChanged(state);
                    listener.onSourceStatusChanged(status);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (!running) return;
                latestState = null;
                status = DataSourceStatus.ERROR;
                Log.w(TAG, "Transport error", throwable);
                if (listener != null) {
                    if (throwable instanceof Exception) {
                        listener.onError((Exception) throwable);
                    } else {
                        listener.onError(new Exception(throwable));
                    }
                    listener.onSourceStatusChanged(status);
                }
            }
        });

        boolean started = transport.start();
        if (!started) {
            connectionMonitor.startFailed();
            status = DataSourceStatus.ERROR;
            if (listener != null) {
                listener.onSourceStatusChanged(status);
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        mainHandler.removeCallbacks(watchdogRunnable);
        transport.setListener(null);
        transport.stop();
        connectionMonitor.stop();
        latestState = null;
        status = DataSourceStatus.STOPPED;
        if (listener != null) {
            listener.onSourceStatusChanged(status);
            listener = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }
}
