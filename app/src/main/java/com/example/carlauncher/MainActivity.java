package com.example.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.carlauncher.databinding.ActivityMainBinding;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.VehicleSendService;
import com.example.carlauncher.ui.CockpitUiState;
import com.example.carlauncher.ui.CockpitViewModel;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

/**
 * 车载启动器主 Activity。
 * 负责 UI 控制、TCP 连接管理以及模拟车辆数据的启动与停止。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VEHICLE_LAUNCHER";
    private static final String CAR_SPEED_PERMISSION =
            "android.car.permission.CAR_SPEED"; // 读取车速所需的权限
    private static final int CAR_PERMISSION_REQUEST_CODE = 100;

    private ActivityMainBinding binding; // 视图绑定
    private Intent serviceIntent; // 启动服务的 Intent
    private VehicleSendService vehicleService;
    private boolean serviceBindingActive;

    private CockpitViewModel viewModel;
    private final VehicleSendService.StateListener serviceStateListener =
            () -> {
                if (viewModel != null) {
                    viewModel.refresh();
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "MainActivity onCreate");
        super.onCreate(savedInstanceState);

        // 初始化视图绑定 (ViewBinding)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this)
                .get(CockpitViewModel.class);

        viewModel.getUiState().observe(this, this::render);

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
            CockpitUiState state = viewModel.getUiState().getValue();

            if (state == null || !state.isActionEnabled()) {
                return;
            }

            if (state.isStopAction()) {
                stopVehicleService();
                return;
            }

            ContextCompat.startForegroundService(this, serviceIntent);
            bindVehicleService(Context.BIND_AUTO_CREATE);
        });

       binding.appsButton.setOnClickListener(view->{
           Intent intent = new Intent(this, AppDrawerActivity.class);
           startActivity(intent);
       });
    }

    private void bindVehicleService(int flags) {
        if (serviceBindingActive) {
            return;
        }

        serviceBindingActive = bindService(
                serviceIntent,
                serviceConnection,
                flags
        );

        Log.i(TAG, "bindService requested: " + serviceBindingActive);
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
     * 根据当前服务的状态（连接中、已连接、正在发送等）更新 UI 上的状态文字和按钮。
     */

    /**
     * 获取服务中最新的车辆状态并渲染到对应的 UI 控件上。
     */
    private void renderVehicleState(VehicleState state) {
        if (binding == null) {
            renderNoData();
            return;
        }

        if (state == null) {
            renderNoData();
            return;
        }


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

    private int resolveConnectionBackground(CockpitUiState state) {
        switch (state.getState()) {
            case ONLINE:
                return R.drawable.bg_status_online;
            case CONNECTING:
            case RECOVERING:
                return R.drawable.bg_status_connecting;
            case DISCONNECTED:
                return R.drawable.bg_status_offline;
            case INVALID_DATA:
                return R.drawable.bg_status_error;

        }
        return R.drawable.bg_status_offline;

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

    private void render(CockpitUiState uiState) {
        if (binding == null) {
            return;
        }

        renderConnectionState(
                uiState.getConnectionLabel(),
                resolveConnectionBackground(uiState),
                uiState.isActionEnabled(),
                uiState.getActionLabel()
        );

        binding.sourceStatusText.setText(
                String.valueOf(uiState.getDataSourceStatus())
        );
        binding.transportStatusText.setText(
                uiState.getTransportLabel()
        );

        VehicleState state = uiState.getVehicleState();
        if (state == null) {
            renderNoData();
            return;
        }

        renderVehicleState(state);
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
                    // 先把 Service 交给 Repository，再注册立即回调的监听器。
                    viewModel.attachService(vehicleService);
                    vehicleService.setStateListener(serviceStateListener);
                    Log.i(TAG, "Activity bound to VehicleSendService");

                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    // 当 Service 进程因崩溃等原因意外终止时触发
                    vehicleService = null;
                    viewModel.detachService();

                    Log.w(TAG, "VehicleSendService disconnected");

                }
            };

    private void unbindVehicleService() {

        if (vehicleService != null) {
            vehicleService.clearStateListener(serviceStateListener);
        }

        viewModel.detachService();

        if (serviceBindingActive) {
            unbindService(serviceConnection);
            serviceBindingActive = false;
        }

        vehicleService = null;
    }

    private void stopVehicleService() {
        unbindVehicleService();
        stopService(serviceIntent);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        binding = null; // 释放视图绑定
        super.onDestroy();
    }

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
        bindVehicleService(0);
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        unbindVehicleService();
        super.onStop();
    }


}
