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
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.someip.NativeVehicleTransport;
import com.example.carlauncher.someip.SomeipConnectionMonitor;

import com.example.carlauncher.someip.VSomeIpDataSource;
import com.example.carlauncher.someip.VSomeIpNativeTransport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Foreground owner of the remote vehicle Event subscription and latest UI snapshot.
 */
public class VehicleSendService extends Service {
    private static final String TAG = "VEHICLE_SERVICE";
    private static final String CHANNEL_ID = "vehicle_send_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    // vSomeIP 生命周期专用单线程执行器，避免 native start/stop 阻塞主线程。
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

        // 初始化 vSomeIP Event 数据源。
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
            notifyStateChanged();
            Log.i(TAG, "VsomeipClient.start=true (on executor thread)");
        } catch (Exception | LinkageError e) {
            Log.e(TAG, "start vsomeip failed", e);
            someipMonitor.startFailed();
            notifyStateChanged();
        }
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
        stateListener = null;
        mainHandler.removeCallbacksAndMessages(null);



        // 停止 vSomeIP：排队到同一单线程执行器（与 start 保持先后顺序），
        // 避免主线程被 native stop 的 join 阻塞；native stop 幂等（未启动则直接返回）。
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
