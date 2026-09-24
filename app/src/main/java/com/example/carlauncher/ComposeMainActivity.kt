package com.example.carlauncher

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.ui.compose.CarLauncherScreen
import com.example.carlauncher.ui.compose.CarLauncherViewModel

class ComposeMainActivity : ComponentActivity() {
    private val viewModel: CarLauncherViewModel by viewModels()
    private var vehicleService: VehicleSendService? = null
    private var bindingActive = false
    private val serviceIntent by lazy { Intent(this, VehicleSendService::class.java) }
    private val listener = VehicleSendService.StateListener { viewModel.refresh() }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val databaseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val message = when {
            result.resultCode != Activity.RESULT_OK -> "已取消数据库页面结果回传"
            data == null || !data.hasExtra(DATABASE_RECORD_COUNT) -> "数据库页面返回结果缺失"
            else -> "数据库页面已完成，当前共 ${data.getIntExtra(DATABASE_RECORD_COUNT, 0)} 条记录"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            vehicleService = (binder as? VehicleSendService.LocalBinder)?.service
            viewModel.attachService(vehicleService)
            vehicleService?.setStateListener(listener)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            vehicleService = null
            viewModel.detachService()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarLauncherScreen(
                viewModel = viewModel,
                onConnectionToggle = ::toggleConnection,
                onOpenApps = { startActivity(Intent(this, AppDrawerActivity::class.java)) },
                onOpenSettings = { startActivity(Intent(this, FragmentLabActivity::class.java)) },
                onOpenBroadcast = { startActivity(Intent(this, BroadcastLabActivity::class.java)) },
                onOpenDatabase = { databaseLauncher.launch(Intent(this, DatabaseLabActivity::class.java)) },
                onOpenDiagnostics = { startActivity(Intent(this, MainActivity::class.java)) },
            )
        }
        if (savedInstanceState == null && ThemePreferences.isWelcomeEnabled(this)) {
            Toast.makeText(this, R.string.welcome_message, Toast.LENGTH_SHORT).show()
        }
    }
    private fun toggleConnection() {
        if (vehicleService != null) {
            releaseBinding()
            stopService(serviceIntent)
            return
        }
        // bindService(flags = 0) can leave a pending registration without delivering a binder.
        // A registration alone must not turn the first Start click into a Stop operation.
        if (bindingActive) releaseBinding()
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
            checkSelfPermission("android.car.permission.CAR_SPEED") != PackageManager.PERMISSION_GRANTED) {
            permissions += "android.car.permission.CAR_SPEED"
        }
        if (permissions.isNotEmpty()) permissionRequest.launch(permissions.toTypedArray())
        try {
            viewModel.setDemo(false)
            ContextCompat.startForegroundService(this, serviceIntent)
            bindingActive = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
            if (!bindingActive) {
                stopService(serviceIntent)
                Toast.makeText(this, "无法绑定车辆接收服务", Toast.LENGTH_LONG).show()
            }
        } catch (error: RuntimeException) {
            releaseBinding()
            Toast.makeText(this, "无法启动接收服务：${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
    override fun onStart() {
        super.onStart()
        if (!bindingActive) bindingActive = bindService(serviceIntent, connection, 0)
    }
    private fun releaseBinding() {
        vehicleService?.clearStateListener(listener)
        if (bindingActive) unbindService(connection)
        bindingActive = false
        vehicleService = null
        viewModel.detachService()
    }
    override fun onStop() {
        releaseBinding()
        super.onStop()
    }

    companion object {
        // Optional result contract: older database pages may simply return RESULT_CANCELED.
        private const val DATABASE_RECORD_COUNT = "com.example.carlauncher.extra.RECORD_COUNT"
    }
}
