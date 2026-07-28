package com.example.carlauncher.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.carlauncher.data.MockVehicleDataSource;

import com.example.carlauncher.R;
import com.example.carlauncher.network.VehicleTcpClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VehicleSendService extends Service {
    private static String TAG = "VEHICLE_SENDSERVICE";
    private static final String CHANNEL_ID = "vehicle_send_channel";
    private static final int NOTIFICATION_ID = 1001;
    private ScheduledExecutorService scheduler;
    private VehicleTcpClient tcpClient;
    private long sequence = 0;
    private MockVehicleDataSource mockDataSource; // 模拟车辆数据源
    private final AtomicBoolean sendingStarted =
            new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");
        // 初始化模拟数据源
        mockDataSource = new MockVehicleDataSource();

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("车辆数据发送中")
                .setContentText("正在向 LVGL 仪表发送车辆状态")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();

        // 必须尽快调用，否则系统会终止 Service。
        startForeground(NOTIFICATION_ID, notification);

        tcpClient = new VehicleTcpClient(
                "192.168.31.248",
                19090
        );
        tcpClient.connect(new VehicleTcpClient.Callback(){
            @Override
            public void onConnected() {
                Log.i(TAG, "TCP connected");
            }

            @Override
            public void onError(Exception exception) {
                Log.e(TAG, "TCP connect failed", exception);
            }
        });
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startSending();

        // 进程被系统回收后，允许系统尝试重建 Service。
        return START_STICKY;
    }

    private void startSending() {
        Log.i(TAG,"startSending");
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        // 只有第一次能从 false 改为 true。
        if (!sendingStarted.compareAndSet(false, true)) {
            Log.i(TAG, "Sending already started, skip");
            return;
        }
        Log.i(TAG, "Start vehicle data sending");
        scheduler.scheduleWithFixedDelay(() -> {
            sequence++;

            int speed = (int) ((sequence * 2) % 201);
            int rpm = 800 + speed * 25;

            String json = "{"
                    + "\"version\":1,"
                    + "\"seq\":" + sequence + ","
                    + "\"timestampMs\":" + System.currentTimeMillis() + ","
                    + "\"speedKph\":" + speed + ","
                    + "\"rpm\":" + rpm + ","
                    + "\"gear\":\"D\","
                    + "\"soc\":79"
                    + "}";

            tcpClient.sendLine(json, new VehicleTcpClient.Callback() {
                @Override
                public void onMessageSent(String sentMessage) {
                    // 数据发送成功，在 UI 上更新最后一次发送的状态
                    if (sequence%100 == 1){

                    Log.i(TAG, "seq=" + sequence
                            + "  speed="
                            + speed
                            + " km/h");
                    }
                }

                @Override
                public void onError(Exception exception) {
                    // 发送失败，停止模拟并显示错误
                    Log.e(TAG, "Send failed", exception);
                }
            });

        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDestroy() {
        sendingStarted.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        if (tcpClient != null) {
            tcpClient.close();
        }
        Log.i(TAG, "Service onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public boolean isRunning() {
        if (mockDataSource.isRunning()) {
            return true;
        }
        return false;
    }

    /**
     * 停止车辆模拟。
     */
    @SuppressLint("SetTextI18n")
    private void stopVehicleSimulation() {
        if (mockDataSource != null) {
            mockDataSource.stop();
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(
                    CHANNEL_ID,
                    "车辆数据发送",
                    NotificationManager.IMPORTANCE_LOW
            );
        }

        NotificationManager manager =
                getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(channel);
        }
    }
}
