package com.example.carlauncher;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.carlauncher.data.VehicleRepository;
import com.example.carlauncher.databinding.ActivityMainBinding;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.VehicleSendService;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

/**
 * 车载启动器主 Activity。
 * 负责 UI 控制、TCP 连接管理以及模拟车辆数据的启动与停止。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VEHICLE_LAUNCHER";
    private static final long UI_REFRESH_INTERVAL_MS = 100L; // UI 刷新间隔（毫秒）
    private static final String CAR_SPEED_PERMISSION =
            "android.car.permission.CAR_SPEED"; // 读取车速所需的权限
    private static final int CAR_PERMISSION_REQUEST_CODE = 100;

    private ActivityMainBinding binding; // 视图绑定
    private Intent serviceIntent; // 启动服务的 Intent
    private VehicleSendService vehicleService; // 绑定的服务实例
    private boolean serviceBound = false; // 是否已绑定服务
    private boolean bindingInProgress = false; // 是否正在进行绑定
    private final Handler uiHandler = new Handler(Looper.getMainLooper()); // 用于定时刷新 UI 的 Handler
    private boolean uiRefreshActive = false; // UI 刷新任务是否处于激活状态
    private final VehicleSendService.StateListener serviceStateListener =
            this::updateServiceUi; // 服务状态变化监听器

    private final VehicleRepository repository =
            new VehicleRepository();

    // UI 定时刷新任务
    private final Runnable vehicleUiRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (!uiRefreshActive) {
                return;
            }
            renderLatestVehicleState(); // 渲染最新的车辆状态数据
            uiHandler.postDelayed(this, UI_REFRESH_INTERVAL_MS); // 安排下一次刷新
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "MainActivity onCreate");
        super.onCreate(savedInstanceState);

        // 初始化视图绑定 (ViewBinding)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 处理系统栏（状态栏、导航栏）的内边距，实现沉浸式体验
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.cockpitRoot,
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left + dp(24),
                            bars.top + dp(24),
                            bars.right + dp(24),
                            bars.bottom + dp(24)
                    );

                    return insets;
                }
        );

        // 初始化 Service Intent
        serviceIntent = new Intent(this, VehicleSendService.class);

        // 如果是车载系统，请求必要的车速读取权限
        requestCarSpeedPermissionIfNeeded();

        // 配置连接/断开按钮的点击逻辑
        binding.connectButton.setOnClickListener(view -> {
            if (serviceBound
                    && vehicleService != null
                    && vehicleService.isSending()) {
                // 如果正在发送数据，点击则停止
                stopVehicleService();
            } else {
                // 否则，启动服务并开始连接/发送
                startVehicleService();
            }
        });

    }

    /**
     * 当没有车辆数据时，重置所有 UI 显示为默认或“无数据”状态。
     */
    private void renderNoData() {
        if (binding == null) {
            return;
        }

        binding.speedText.setText("-- km/h");
        binding.gearText.setText("-");
        binding.rpmText.setText("-- rpm");
        binding.batteryText.setText("-- %");
        binding.evBatteryText.setText("-- %");
        binding.coolantTempText.setText("-- °C");

        binding.turnSignalText.setText("--");
        binding.parkingBrakeText.setText("--");
        binding.doorLockText.setText("--");

        binding.warningText.setText("NO DATA");
        binding.validityText.setText("INVALID");
        binding.sequenceText.setText("Seq --");

        binding.sourceStatusText.setText("STOPPED");
        binding.transportStatusText.setText("Transport: OFFLINE");
        binding.lastUpdateText.setText("Waiting for vehicle data");
    }

    /**
     * 将 dp 值转换为像素值。
     */
    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    /**
     * 如果运行在 Android Automotive 系统上，检查并请求车速相关权限。
     */
    private void requestCarSpeedPermissionIfNeeded() {
        boolean isAutomotive = getPackageManager().hasSystemFeature(
                "android.hardware.type.automotive"
        );
        if (isAutomotive
                && checkSelfPermission(CAR_SPEED_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{CAR_SPEED_PERMISSION},
                    CAR_PERMISSION_REQUEST_CODE
            );
        }
    }

    /**
     * 启动 VehicleSendService 服务（前台服务）并执行绑定操作。
     */
    private void startVehicleService() {
        ContextCompat.startForegroundService(this, serviceIntent);

        if (!serviceBound && !bindingInProgress) {
            bindingInProgress = bindService(
                    serviceIntent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
            );
        }
    }

    /**
     * 停止 VehicleSendService 服务。
     * 包括解绑服务、移除监听器和停止服务本身。
     */
    private void stopVehicleService() {


        if (serviceBound || bindingInProgress) {
            if (vehicleService != null) {
                vehicleService.clearStateListener(serviceStateListener);
            }
            unbindService(serviceConnection);
            serviceBound = false;
            bindingInProgress = false;
            vehicleService = null;
        }

        stopService(serviceIntent);

        updateServiceUi();
        renderNoData();
    }

    /**
     * 根据当前服务的状态（连接中、已连接、正在发送等）更新 UI 上的状态文字和按钮。
     */
    private void updateServiceUi() {
        Log.i(TAG, "updateServiceUi");

        if (binding == null) {
            return;
        }

        if (!serviceBound || vehicleService == null) {
            binding.connectionStatusText.setText("SERVICE UNBOUND");
            binding.connectButton.setText("START SEND");
            binding.connectButton.setEnabled(true);
            return;
        }

        if (vehicleService.isSending()) {
            binding.connectionStatusText.setText("SENDING");
            binding.connectButton.setText("STOP SEND");
            binding.connectButton.setEnabled(true);

        } else if (vehicleService.isConnecting()) {
            binding.connectionStatusText.setText("CONNECTING");
            binding.connectButton.setText("PLEASE WAIT");
            binding.connectButton.setEnabled(false);

        } else if (vehicleService.isTcpConnected()) {
            binding.connectionStatusText.setText("CONNECTED");
            binding.connectButton.setText("START SEND");
            binding.connectButton.setEnabled(true);

        } else {
            binding.connectionStatusText.setText("DISCONNECTED");
            binding.connectButton.setText("START SEND");
            binding.connectButton.setEnabled(true);
        }
    }

    /**
     * 获取服务中最新的车辆状态并渲染到对应的 UI 控件上。
     */
    private void renderLatestVehicleState() {
        if (binding == null || !serviceBound || vehicleService == null) {
            renderNoData();
            return;
        }

        VehicleState state = vehicleService.getLatestVehicleState();
        if (state == null) {
            renderNoData();
            return;
        }
        
        // 更新数据源状态
        binding.sourceStatusText.setText(
                String.valueOf(vehicleService.getSourceStatus())
        );

        // 更新传输层（TCP）连接状态
        binding.transportStatusText.setText(
                vehicleService.isTcpConnected()
                        ? "Transport: ONLINE"
                        : "Transport: OFFLINE"
        );

        // 更新最后更新时间/序列号
        binding.lastUpdateText.setText(
                "Updated · Seq " + state.getSequence()
        );
        
        // 更新各项车辆动态指标
        binding.speedText.setText(
                getString(R.string.speed_value, state.getVehSpeedKph())
        );
        binding.gearText.setText(String.valueOf(state.getGear()));
        binding.rpmText.setText(
                getString(R.string.rpm_value, state.getEngRpm())
        );
        binding.batteryText.setText(
                getString(R.string.battery_value, state.getSoc())
        );

        binding.turnSignalText.setText(
                String.valueOf(state.getTurnSignal())
        );
        binding.parkingBrakeText.setText(
                state.isParkingBrake() ? "ON" : "OFF"
        );
        binding.warningText.setText(
                String.valueOf(state.getWarning())
        );
        binding.validityText.setText(
                String.valueOf(state.getValidity())
        );
        binding.sequenceText.setText(
                "Seq " + state.getSequence()
        );

        renderNullableFields(state);
    }

    /**
     * 渲染可能为空的字段（可选属性）。
     */
    private void renderNullableFields(VehicleState state) {
        Boolean doorLock = state.getDoorLock();
        binding.doorLockText.setText(
                doorLock == null ? "--" : doorLock ? "LOCKED" : "UNLOCKED"
        );

        Float coolant = state.getEngineCoolantTemp();
        binding.coolantTempText.setText(
                coolant == null ? "-- °C" : Math.round(coolant) + " °C"
        );

        Float battery = state.getEvBatteryLevel();
        binding.evBatteryText.setText(
                battery == null ? "-- %" : Math.round(battery) + " %"
        );
    }

    private void renderConnectionState(
            String label,
            int backgroundRes,
            boolean buttonEnabled,
            String buttonLabel
    ) {
        binding.connectionStatusText.setText(label);
        binding.connectionStatusText.setBackgroundResource(backgroundRes);
        binding.connectButton.setEnabled(buttonEnabled);
        binding.connectButton.setText(buttonLabel);
    }

    /**
     * 开启 UI 定时刷新。通常在 Activity 可见时调用。
     */
    private void startVehicleUiRefresh() {
        if (uiRefreshActive) {
            return;
        }
        uiRefreshActive = true;
        Log.i(TAG, "Start lifecycle-aware UI refresh (100 ms)");
        uiHandler.post(vehicleUiRefreshTask);
    }

    /**
     * 停止 UI 定时刷新。通常在 Activity 不可见或销毁时调用。
     */
    private void stopVehicleUiRefresh() {
        if (!uiRefreshActive) {
            return;
        }
        uiRefreshActive = false;
        uiHandler.removeCallbacks(vehicleUiRefreshTask);
        Log.i(TAG, "Stop lifecycle-aware UI refresh");
    }

    /**
     * 显示错误信息并重置 UI 状态。
     */
    @SuppressLint("SetTextI18n")
    private void showError(Exception exception) {
        Log.e(TAG, "TCP operation failed", exception);

        runOnUiThread(() -> {
            if (binding == null) {
                return;
            }
            binding.connectionStatusText.setText("ERROR");


            Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        stopVehicleUiRefresh();
        binding = null; // 释放视图绑定
        super.onDestroy();
    }

    /**
     * 处理与 VehicleSendService 建立绑定的连接回调。
     */
    private final ServiceConnection serviceConnection =
            new ServiceConnection() {

                @Override
                public void onServiceConnected(
                        ComponentName name,
                        IBinder binder
                ) {
                    // 获取 Service 实例并设置监听器
                    VehicleSendService.LocalBinder localBinder =
                            (VehicleSendService.LocalBinder) binder;


                    vehicleService = localBinder.getService();
                    serviceBound = true;

                    repository.attachService(vehicleService);

                    bindingInProgress = false;
                    vehicleService.setStateListener(serviceStateListener);

                    Log.i(TAG, "Activity bound to VehicleSendService");

                    // 绑定成功后立即更新 UI 状态
                    updateServiceUi();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    // 当 Service 进程因崩溃等原因意外终止时触发

                    if (!serviceBound || vehicleService == null) {
                        binding.connectionStatusText.setText("SERVICE UNBOUND");
                        binding.connectButton.setText("START SEND");
                        binding.connectButton.setEnabled(true);
                        renderNoData();
                        return;
                    }

                    vehicleService = null;
                    serviceBound = false;
                    bindingInProgress = false;

                    Log.w(TAG, "VehicleSendService disconnected");

                    updateServiceUi();
                }
            };

    @Override
    protected void onResume() {
        super.onResume();
//        startVehicleRefresh();
        Log.i(TAG, "MainActivity onResume");
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        Log.i(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
        startVehicleUiRefresh();

        if (!serviceBound && !bindingInProgress) {
            // 只绑定已经运行的 Service，避免进入 Activity 时自动启动它。
            bindingInProgress = bindService(
                    serviceIntent,
                    serviceConnection,
                    0
            );
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        stopVehicleUiRefresh();

        if (serviceBound || bindingInProgress) {
            if (vehicleService != null) {
                vehicleService.clearStateListener(serviceStateListener);
            }
            repository.detachService();
            unbindService(serviceConnection);
            serviceBound = false;
            bindingInProgress = false;
            vehicleService = null;
        }

        super.onStop();
    }


}
