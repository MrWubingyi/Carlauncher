package com.example.carlauncher.ui.home

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
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.carlauncher.R
import com.example.carlauncher.ThemePreferences
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.ui.apps.AppDrawerActivity
import com.example.carlauncher.ui.compose.CarLauncherScreen
import com.example.carlauncher.ui.compose.CarLauncherViewModel
import com.example.carlauncher.ui.lab.broadcast.BroadcastLabActivity
import com.example.carlauncher.ui.lab.database.DatabaseLabActivity
import com.example.carlauncher.ui.settings.FragmentLabActivity

class ComposeMainActivity : ComponentActivity() {
    private val viewModel: CarLauncherViewModel by viewModels()
    private var vehicleService: VehicleSendService? = null
    private var bindingActive = false
    private val serviceIntent by lazy { Intent(this, VehicleSendService::class.java) }
    private val listener = VehicleSendService.StateListener { viewModel.refresh() }
    private val permissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return@registerForActivityResult
            }
            // 本次请求也可能只包含车辆权限，
            // 没有通知权限这一项时，不提示“通知权限被拒绝”。
            val notificationGranted =
                grants[Manifest.permission.POST_NOTIFICATIONS]

            if (notificationGranted != null) {
                val message = when {
                    notificationGranted ->
                        "通知权限已授予"

                    shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) ->
                        "未授予通知权限；需要通知时，可再次申请"

                    else ->
                        "未授予通知权限；如需开启，请到系统设置中检查"
                }

                Log.i(TAG, message)
                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    private val databaseLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val message =
                when {
                    result.resultCode != Activity.RESULT_OK -> "已取消数据库页面结果回传"
                    data == null || !data.hasExtra(DATABASE_RECORD_COUNT) -> "数据库页面返回结果缺失"
                    else -> "数据库页面已完成，当前共 ${
                        data.getIntExtra(
                            DATABASE_RECORD_COUNT,
                            0
                        )
                    } 条记录"
                }
            Log.i(TAG, "Database result: $message")
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    private val connection =
        object : ServiceConnection {
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
        Log.i(TAG,"on Create")
        enableEdgeToEdge()
        setContent {
            CarLauncherScreen(
                viewModel = viewModel,
                onConnectionToggle = ::toggleConnection,
                onOpenApps = { startActivity(Intent(this, AppDrawerActivity::class.java)) },
                onOpenSettings = { startActivity(Intent(this, FragmentLabActivity::class.java)) },
                onOpenBroadcast = { startActivity(Intent(this, BroadcastLabActivity::class.java)) },
                onOpenDatabase = {
                    databaseLauncher.launch(Intent(this, DatabaseLabActivity::class.java))
                },
            )
        }
        if (savedInstanceState == null && ThemePreferences.isWelcomeEnabled(this)) {
            Log.i(TAG, "Welcome message displayed")
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
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (
            packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
            checkSelfPermission("android.car.permission.CAR_SPEED") !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions += "android.car.permission.CAR_SPEED"
        }
        if (permissions.isNotEmpty()) permissionRequest.launch(permissions.toTypedArray())
        try {
            viewModel.setDemo(false)
            ContextCompat.startForegroundService(this, serviceIntent)
            bindingActive = bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
            if (!bindingActive) {
                stopService(serviceIntent)
                Log.e(TAG, "Unable to bind VehicleSendService")
                Toast.makeText(this, "无法绑定车辆接收服务", Toast.LENGTH_LONG).show()
            }
        } catch (error: RuntimeException) {
            releaseBinding()
            Log.e(TAG, "Unable to start VehicleSendService", error)
            Toast.makeText(this, "无法启动接收服务：${error.localizedMessage}", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG,"on start")

        if (!bindingActive) bindingActive = bindService(serviceIntent, connection, 0)
    }

    override fun onResume() {
        super.onResume()

        val message = if (Build.VERSION.SDK_INT >= 33) {
            val granted =
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED

            if (granted) {
                "页面恢复：通知权限已授予"
            } else {
                "页面恢复：通知权限未授予"
            }
        } else {
            "页面恢复：当前系统无需申请 POST_NOTIFICATIONS"
        }

        Log.i(TAG, message)
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
        Log.i(TAG,"on stop")
        super.onStop()
    }

    companion object {
        private const val TAG = "COMPOSE_MAIN"

        private const val DATABASE_RECORD_COUNT = "com.example.carlauncher.extra.RECORD_COUNT"
    }
}
