package com.example.carlauncher.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.carlauncher.R;
import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.SourceType;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.data.VehicleDataSourceFactory;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.network.VehicleTcpClient;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 前台服务，负责管理车辆数据源和 LVGL TCP 传输。
 * 数据源的生命周期与 TCP 连接的生命周期是独立的。
 * <p>
 * Foreground service that owns the vehicle data source and LVGL TCP transport.
 * The data source lifecycle is independent from the TCP connection lifecycle.
 */
public class VehicleSendService extends Service {
    private static final String TAG = "VEHICLE_SERVICE";
    private static final String CHANNEL_ID = "vehicle_send_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long RECONNECT_DELAY_SECONDS = 2L;

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 连接状态标志
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    // 是否已开始发送数据标志
    private final AtomicBoolean sendingStarted = new AtomicBoolean(false);
    // 是否已调度重连标志
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private ScheduledExecutorService reconnectScheduler;
    private VehicleTcpClient tcpClient;
    private VehicleDataSource vehicleDataSource;

    private volatile boolean stopping;
    private volatile VehicleState latestVehicleState;
    private volatile DataSourceStatus sourceStatus = DataSourceStatus.STOPPED;
    private StateListener stateListener;

    /**
     * 服务状态监听接口
     */
    public interface StateListener {
        /**
         * 当连接状态或数据源状态发生变化时回调
         */
        void onStateChanged();
    }

    /**
     * 车辆数据源监听器，处理数据更新和状态变更
     */
    private final VehicleDataSource.Listener dataListener =
            new VehicleDataSource.Listener() {
                @Override
                public void onStateChanged(VehicleState state) {
                    if (stopping || state == null) {
                        return;
                    }

                    latestVehicleState = state;
                    // 发送车辆状态到 TCP 客户端
                    sendVehicleState(state);
                }

                @Override
                public void onSourceStatusChanged(DataSourceStatus status) {
                    if (status == null) {
                        return;
                    }

                    sourceStatus = status;
                    if (!stopping) {
                        notifyStateChanged();
                    }
                }

                @Override
                public void onError(Exception exception) {
                    Log.e(TAG, "Vehicle data source error", exception);
                    sourceStatus = DataSourceStatus.ERROR;
                    if (!stopping) {
                        notifyStateChanged();
                    }
                }
            };

    /**
     * 用于与 Activity 绑定的 Binder 类
     */
    public class LocalBinder extends Binder {
        public VehicleSendService getService() {
            return VehicleSendService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");

        // 创建通知渠道并启动前台服务
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // 初始化重连调度器、TCP 客户端和车辆数据源
        reconnectScheduler =
                Executors.newSingleThreadScheduledExecutor();
        tcpClient = new VehicleTcpClient(
                "192.168.31.248",
                19090
        );
        vehicleDataSource = VehicleDataSourceFactory.create(
                this,
                SourceType.MOCK
        );

        // 启动数据源并连接 TCP
        startVehicleDataSource();
        connectTcp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 服务被系统杀死后尝试重启
        return START_STICKY;
    }

    /**
     * 启动车辆数据源
     */
    private void startVehicleDataSource() {
        if (stopping
                || vehicleDataSource == null
                || vehicleDataSource.isRunning()) {
            return;
        }

        try {
            sourceStatus = DataSourceStatus.CONNECTING;
            notifyStateChanged();
            vehicleDataSource.start(dataListener);
        } catch (RuntimeException exception) {
            sourceStatus = DataSourceStatus.ERROR;
            Log.e(TAG, "Start vehicle data source failed", exception);
            notifyStateChanged();
        }
    }

    /**
     * 发起 TCP 连接
     */
    private void connectTcp() {
        if (stopping
                || tcpClient == null
                || tcpClient.isConnected()) {
            return;
        }

        // 确保只有一个连接任务在进行
        if (!connecting.compareAndSet(false, true)) {
            Log.d(TAG, "TCP connection already in progress");
            return;
        }

        Log.i(TAG, "Try connect TCP");
        notifyStateChanged();

        tcpClient.connect(new VehicleTcpClient.Callback() {
            @Override
            public void onConnected() {
                connecting.set(false);
                reconnectScheduled.set(false);

                if (stopping) {
                    return;
                }

                sendingStarted.set(true);
                Log.i(TAG, "TCP connected; vehicle frames can be sent");

                VehicleState state = latestVehicleState;
                if (state != null) {
                    sendVehicleState(state);
                }
                notifyStateChanged();
            }

            @Override
            public void onError(Exception exception) {
                connecting.set(false);
                sendingStarted.set(false);

                if (stopping) {
                    return;
                }

                Log.e(
                        TAG,
                        "TCP connect failed; retry after 2 seconds",
                        exception
                );
                notifyStateChanged();
                // 连接失败，调度重连
                scheduleReconnect();
            }
        });
    }

    /**
     * 将车辆状态序列化为 JSON 并通过 TCP 发送
     */
    private void sendVehicleState(VehicleState state) {
        VehicleTcpClient client = tcpClient;
        if (stopping
                || !sendingStarted.get()
                || client == null
                || !client.isConnected()) {
            return;
        }
        final String json;
        try {
            json = state.toJson();
            if (state.getSequence() % 20 == 1) {
                Log.d(TAG, "json=" + json);
            }
            Log.i(TAG, "json = " + json);
        } catch (Exception exception) {
            Log.e(TAG, "VehicleState JSON failed", exception);
            return;
        }

        client.sendLine(json, new VehicleTcpClient.Callback() {
            @Override
            public void onMessageSent(String message) {
                long sequence = state.getSequence();
                // 每发送 20 帧打印一次日志 (约 2 秒)，并包含告警状态
                if (sequence % 20 == 1) {
                    Log.i(
                            TAG,
                            "seq=" + sequence
                                    + " speed=" + state.getVehSpeedKph()
                                    + " brk=" + state.isParkingBrake()
                                    + " lock=" + state.getDoorLock()
                                    + " belt=" + state.getBeltWarning()
                                    + " warn=" + state.getWarning().getValue()
                    );
                }
            }

            @Override
            public void onError(Exception exception) {
                Log.e(TAG, "Send failed", exception);
                // 发送失败，处理连接丢失
                handleConnectionLost();
            }
        });
    }

    /**
     * 处理连接丢失的情况
     */
    private void handleConnectionLost() {
        if (stopping
                || !sendingStarted.compareAndSet(true, false)) {
            return;
        }

        Log.w(
                TAG,
                "TCP connection lost; keep data source running"
        );
        connecting.set(false);

        if (tcpClient != null) {
            tcpClient.close();
        }

        notifyStateChanged();
        // 调度自动重连
        scheduleReconnect();
    }

    /**
     * 调度 TCP 重连任务
     */
    private void scheduleReconnect() {
        ScheduledExecutorService scheduler = reconnectScheduler;
        if (stopping
                || scheduler == null
                || scheduler.isShutdown()
                || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            scheduler.schedule(
                    () -> {
                        reconnectScheduled.set(false);
                        connectTcp();
                    },
                    RECONNECT_DELAY_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (RejectedExecutionException exception) {
            reconnectScheduled.set(false);
            if (!stopping) {
                Log.e(TAG, "Schedule TCP reconnect failed", exception);
            }
        }
    }

    /**
     * 是否正在发送数据
     */
    public boolean isSending() {
        return sendingStarted.get() && isTcpConnected();
    }

    /**
     * 是否正在尝试连接
     */
    public boolean isConnecting() {
        return connecting.get();
    }

    /**
     * TCP 是否已连接
     */
    public boolean isTcpConnected() {
        return tcpClient != null && tcpClient.isConnected();
    }

    /**
     * 获取最新的车辆状态
     */
    public VehicleState getLatestVehicleState() {
        return latestVehicleState;
    }

    /**
     * 获取数据源状态
     */
    public DataSourceStatus getSourceStatus() {
        return sourceStatus;
    }

    /**
     * 设置状态监听器
     */
    public void setStateListener(StateListener listener) {
        stateListener = listener;
        notifyStateChanged();
    }

    /**
     * 清除状态监听器
     */
    public void clearStateListener(StateListener listener) {
        if (stateListener == listener) {
            stateListener = null;
        }
    }

    /**
     * 通知状态已变更，在主线程执行
     */
    private void notifyStateChanged() {
        if (stopping) {
            return;
        }

        mainHandler.post(() -> {
            StateListener listener = stateListener;
            if (!stopping && listener != null) {
                listener.onStateChanged();
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Service onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service onDestroy");
        stopping = true;
        connecting.set(false);
        sendingStarted.set(false);
        reconnectScheduled.set(false);
        stateListener = null;
        mainHandler.removeCallbacksAndMessages(null);

        // 停止并清理资源
        if (vehicleDataSource != null) {
            vehicleDataSource.stop();
            vehicleDataSource = null;
        }

        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }

        if (tcpClient != null) {
            tcpClient.shutdown();
            tcpClient = null;
        }

        sourceStatus = DataSourceStatus.STOPPED;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Vehicle data service")
                .setContentText("Sending vehicle state to LVGL")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Vehicle data",
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager manager =
                getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
