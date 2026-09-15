package com.example.carlauncher.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.carlauncher.MainActivity;
import com.example.carlauncher.R;
import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.data.VehicleProtocol;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.network.VehicleTcpClient;
import com.example.carlauncher.someip.NativeVehicleTransport;
import com.example.carlauncher.someip.SomeipConnectionMonitor;

import com.example.carlauncher.someip.VSomeIpDataSource;
import com.example.carlauncher.someip.VSomeIpNativeTransport;
import com.example.carlauncher.someip.VsomeipClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Foreground owner of the remote vehicle Event subscription and latest UI snapshot.
 * Historical class name and TCP inspection methods are retained for compatibility.
 */
public class VehicleSendService extends Service {
    private static final String TAG = "VEHICLE_SERVICE";
    private static final String CHANNEL_ID = "vehicle_send_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long RECONNECT_DELAY_SECONDS = VehicleProtocol.RECONNECT_DELAY_SECONDS;

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 连接状态标志
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    // 是否已开始发送数据标志
    private final AtomicBoolean sendingStarted = new AtomicBoolean(false);
    // 是否已调度重连标志
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private ScheduledExecutorService reconnectScheduler;
    // vSomeIP 生命周期专用单线程执行器：native start/stop 与 10 Hz 发送解耦，
    // 避免在主线程上执行 vsomeip（其 start() 会阻塞）导致 UI/Service.onCreate 卡死。
    private ExecutorService vsomeipExecutor;
    // The native client is process-wide; serialize lifecycle across Service recreation too.
    private static final class SomeipLifecycle {
        static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    }
    private final SomeipConnectionMonitor someipMonitor = new SomeipConnectionMonitor();
    private NativeVehicleTransport nativeTransport;
    private VSomeIpDataSource vsomeipDataSource;

    public SomeipConnectionMonitor.Snapshot getSomeipStatus() {
        return vsomeipDataSource != null ? vsomeipDataSource.getConnectionMonitor().snapshot() : someipMonitor.snapshot();
    }
    private VehicleTcpClient tcpClient;
    private VehicleDataSource vehicleDataSource;

    private volatile boolean stopping;
    // vSomeIP 是否已启动成功（native 不可用时跳过 10 Hz 发送，避免空转 JNI 调用）
    private volatile boolean vsomeipStarted;
    private volatile VehicleState latestVehicleState;
    private volatile DataSourceStatus sourceStatus = DataSourceStatus.STOPPED;
    private StateListener stateListener;

    private final AtomicReference<TcpConnectionState> tcpState =
            new AtomicReference<>(TcpConnectionState.DISCONNECTED);

    private void transitionTo(
            TcpConnectionState next,
            String reason
    ) {
        TcpConnectionState previous = tcpState.getAndSet(next);
        if (previous == next) {
            return;
        }

        Log.i(TAG, "TCP_STATE " + previous + " -> " + next
                + " reason=" + reason);

        notifyStateChanged();
    }

    public TcpConnectionState getTcpState() {
        return tcpState.get();
    }

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
                    // vSomeIP 路径（TCP 保留作对照；native 侧在 Service 不可用/未 init 时自动跳过）
                    if (vsomeipStarted || (nativeTransport != null && nativeTransport.isAvailable())) {
                        try {
                            VsomeipClient.buildAndSend(
                                    (int) state.getSequence(),
                                    state.getTimestampMs(),
                                    state.getVehSpeedKph(),
                                    state.getGear() == null ? 0xFF : state.getGear().getValue(),
                                    state.getDataStatus() == null ? 0xFF : state.getDataStatus().getValue());
                        } catch (Exception e) {
                            Log.e(TAG, "vsomeip send failed", e);
                        }
                    }
                    notifyStateChanged();
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
        Log.i(TAG, "Service onCreate id=" + System.identityHashCode(this));

        // 创建通知渠道并启动前台服务
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // 初始化重连调度器、TCP 客户端和车辆数据源
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        vsomeipExecutor = SomeipLifecycle.EXECUTOR;
        sourceStatus = DataSourceStatus.CONNECTING;

        // 固定生命周期所有权：Service 创建时初始化对象，但不重复启动
        nativeTransport = new VSomeIpNativeTransport(this);
        vsomeipDataSource = new VSomeIpDataSource(nativeTransport, someipMonitor);

        startVsomeipClient();
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
     * 调度 vSomeIP 启动到专用单线程执行器（不占主线程；native start 内部不再阻塞）。
     */
    private void startVsomeipClient() {
        notifyStateChanged();
        ExecutorService executor = vsomeipExecutor;
        if (executor == null || executor.isShutdown()) {
            Log.w(TAG, "vsomeip executor unavailable, skip start");
            someipMonitor.startFailed();
            notifyStateChanged();
            return;
        }
        try {
            executor.execute(this::runVsomeipStart);
        } catch (RejectedExecutionException exception) {
            Log.e(TAG, "schedule vsomeip start rejected", exception);
            someipMonitor.startFailed();
            notifyStateChanged();
        }
    }

    /**
     * 在 vsomeipExecutor 线程启动 VSomeIpDataSource。
     */
    private void runVsomeipStart() {
        if (stopping || vsomeipDataSource == null) return;
        try {
            vsomeipDataSource.start(dataListener);
            vsomeipStarted = vsomeipDataSource.isRunning() || vsomeipDataSource.getTransport().isAvailable();
            notifyStateChanged();
            Log.i(TAG, "VsomeipClient.start=true (on executor thread)");
        } catch (Exception | LinkageError e) {
            Log.e(TAG, "start vsomeip failed", e);
            someipMonitor.startFailed();
            notifyStateChanged();
        }
    }

    /**
     * 发起 TCP 连接
     */
    private void connectTcp() {
        if (stopping || getTcpState() == TcpConnectionState.ONLINE) {
            return;
        }
        // 确保只有一个连接任务在进行
        if (!connecting.compareAndSet(false, true)) {
            Log.d(TAG, "TCP connection already in progress");
            return;
        }

        TcpConnectionState current = tcpState.get();
        transitionTo(
                current == TcpConnectionState.DISCONNECTED
                        ? TcpConnectionState.CONNECTING
                        : TcpConnectionState.RECOVERING,
                "connect requested"
        );

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
                // TCP 已建立，但尚未证明数据可正常发送。
                transitionTo(
                        TcpConnectionState.RECOVERING,
                        "socket connected"
                );

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
                transitionTo(
                        TcpConnectionState.DISCONNECTED,
                        "connect failed: " + exception.getMessage()
                );

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
        } catch (Exception exception) {
            Log.e(TAG, "VehicleState JSON failed", exception);
            return;
        }

        client.sendLine(json, new VehicleTcpClient.Callback() {
            @Override
            public void onMessageSent(String message) {
                if (state.getValidity() == DataValidity.VALID) {
                    transitionTo(
                            TcpConnectionState.ONLINE,
                            "first valid frame sent"
                    );
                }
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
                handleConnectionLost(exception);
            }
        });
    }

    /**
     * 处理连接丢失的情况
     */
    private void handleConnectionLost(Exception exception) {
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
        transitionTo(
                TcpConnectionState.DISCONNECTED,
                "send failed: " + exception.getMessage()
        );
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
        transitionTo(
                TcpConnectionState.RECOVERING,
                "retry scheduled"
        );

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
        return getTcpState() == TcpConnectionState.CONNECTING;
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
        if (latestVehicleState != null) {
            return latestVehicleState;
        }
        return vsomeipDataSource != null ? vsomeipDataSource.getLatestState() : null;
    }

    /**
     * 获取数据源状态
     */
    public DataSourceStatus getSourceStatus() {
        if (vsomeipDataSource != null && vsomeipDataSource.getStatus() != DataSourceStatus.STOPPED) {
            return vsomeipDataSource.getStatus();
        }
        return sourceStatus;
    }

    public DataValidity getDataValidity() {
        VehicleState state = getLatestVehicleState();
        if (null != state) {
            return state.getValidity();
        }
        return DataValidity.INCOMPLETE;
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
        Log.i(TAG, "Service onBind id=" + System.identityHashCode(this));
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Service onUnbind id=" + System.identityHashCode(this));
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service onDestroy id=" + System.identityHashCode(this));
        stopping = true;
        someipMonitor.stop();
        connecting.set(false);
        sendingStarted.set(false);
        reconnectScheduled.set(false);
        stateListener = null;
        mainHandler.removeCallbacksAndMessages(null);
        transitionTo(
                TcpConnectionState.DISCONNECTED,
                "on destory"
        );
        // 停止并清理资源
        if (vehicleDataSource != null) {
            vehicleDataSource.stop();
            vehicleDataSource = null;
        }

        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }

        // 停止 vSomeIP：排队到同一单线程执行器（与 start 保持先后顺序），
        // 避免主线程被 native stop 的 join 阻塞；native stop 幂等（未启动则直接返回）。
        vsomeipStarted = false;
        ExecutorService vsomeipExecutorToStop = vsomeipExecutor;
        vsomeipExecutor = null;
        if (vsomeipExecutorToStop != null) {
            try {
                vsomeipExecutorToStop.execute(() -> {
                    try {
                        if (vsomeipDataSource != null) {
                            vsomeipDataSource.stop();
                        }
                        Log.i(TAG, "SOMEIP_RELEASED id=" + System.identityHashCode(this));
                    } catch (Throwable t) {
                        Log.e(TAG, "vsomeip stop failed", t);
                    }
                });
            } catch (RejectedExecutionException exception) {
                Log.e(TAG, "schedule vsomeip stop rejected", exception);
            }
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
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                1001,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Vehicle data service")
                .setContentText("Subscribing to vehicle state events")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setForegroundServiceBehavior(
                        NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                )
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
