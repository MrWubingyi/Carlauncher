package com.example.carlauncher;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.carlauncher.databinding.ActivityMainBinding;
import com.example.carlauncher.service.VehicleSendService;

/**
 * 车载启动器主 Activity。
 * 负责 UI 控制、TCP 连接管理以及模拟车辆数据的启动与停止。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CAR_LAUNCHER";

    private ActivityMainBinding binding;


     private  Intent serviceIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化视图绑定 (ViewBinding)
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Log.i(TAG, "MainActivity onCreate");


        // 连接按钮点击事件
        binding.connectButton.setOnClickListener(view -> connectToUbuntu());


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
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");

        super.onStop();
    }

    /**
     * 启动车辆模拟并将生成的数据通过 TCP 发送。
     */
    @SuppressLint("SetTextI18n")
    private void startVehicleSimulation() {

    }

    /**
     * 连接到 Ubuntu 服务器。
     */
    private void connectToUbuntu() {

        serviceIntent = new Intent(this, VehicleSendService.class);

        ContextCompat.startForegroundService(this, serviceIntent);

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
        stopService(serviceIntent);
        binding = null; // 释放视图绑定
        super.onDestroy();
    }
}
