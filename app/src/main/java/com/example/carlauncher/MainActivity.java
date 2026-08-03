package com.example.carlauncher;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.carlauncher.databinding.ActivityMainBinding;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.VehicleSendService;

/**
 * 车载启动器主 Activity。
 * 负责 UI 控制、TCP 连接管理以及模拟车辆数据的启动与停止。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VEHICLE_LAUNCHER";
    private static final long UI_REFRESH_INTERVAL_MS = 100L;

    private ActivityMainBinding binding;
    private Intent serviceIntent;
    private VehicleSendService vehicleService;
    private boolean serviceBound = false;
    private boolean bindingInProgress = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean uiRefreshActive = false;
    private final VehicleSendService.StateListener serviceStateListener =
            this::updateServiceUi;
    private final Runnable vehicleUiRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (!uiRefreshActive) {
                return;
            }
            renderLatestVehicleState();
            uiHandler.postDelayed(this, UI_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "MainActivity onCreate");
        super.onCreate(savedInstanceState);

        // 初始化视图绑定 (ViewBinding)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        serviceIntent = new Intent(this, VehicleSendService.class);
        // 连接按钮点击事件
        binding.connectButton.setOnClickListener(view -> {
            if (serviceBound
                    && vehicleService != null
                    && vehicleService.isSending()) {
                stopVehicleService();
            } else {
                startVehicleService();
            }
        });

    }

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
    }

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

    private void renderLatestVehicleState() {
        if (binding == null || !serviceBound || vehicleService == null) {
            return;
        }

        VehicleState state = vehicleService.getLatestVehicleState();
        if (state == null) {
            binding.lastMessageText.setText("Waiting for vehicle data");
            return;
        }

        binding.lastMessageText.setText(
                "Speed: " + state.getVehSpeedKph() + " km/h"
                        + "\nGear: " + state.getGear()
                        + "\nStatus: "
                        + (vehicleService.isTcpConnected() ? "NORMAL" : "OFFLINE")
                        + "\nSequence: " + state.getSequence()
        );
    }

    private void startVehicleUiRefresh() {
        if (uiRefreshActive) {
            return;
        }
        uiRefreshActive = true;
        Log.i(TAG, "Start lifecycle-aware UI refresh (100 ms)");
        uiHandler.post(vehicleUiRefreshTask);
    }

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

    private final ServiceConnection serviceConnection =
            new ServiceConnection() {

                @Override
                public void onServiceConnected(
                        ComponentName name,
                        IBinder binder
                ) {

                    VehicleSendService.LocalBinder localBinder =
                            (VehicleSendService.LocalBinder) binder;

                    vehicleService = localBinder.getService();
                    serviceBound = true;
                    bindingInProgress = false;
                    vehicleService.setStateListener(serviceStateListener);

                    Log.i(TAG, "Activity bound to VehicleSendService");

                    updateServiceUi();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    // Service 进程意外终止时可能触发。
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
            unbindService(serviceConnection);
            serviceBound = false;
            bindingInProgress = false;
            vehicleService = null;
        }

        super.onStop();
    }


}
