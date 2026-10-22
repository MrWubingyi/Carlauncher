package com.example.carlauncher.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.ui.home.map.MapTaskContainer
import androidx.activity.viewModels
import com.example.carlauncher.R
import com.example.carlauncher.ThemePreferences
import com.example.carlauncher.ui.apps.AppDrawerActivity
import com.example.carlauncher.ui.compose.CarLauncherScreen
import com.example.carlauncher.ui.compose.CarLauncherViewModel
import com.example.carlauncher.ui.lab.broadcast.BroadcastLabActivity
import com.example.carlauncher.ui.lab.database.DatabaseLabActivity
import com.example.carlauncher.ui.settings.FragmentLabActivity

/**
 * Selects the home layout, assembles cards and mounts the optional map task view.
 * Host/service/task lifetime belongs to CarLauncherViewModel; card logic to HomeCardModule.
 */
class ComposeMainActivity : ComponentActivity() {
    private val viewModel: CarLauncherViewModel by viewModels()
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG,"on Create")
        enableEdgeToEdge()
        setContent {
            val mapTask by viewModel.mapTask.collectAsStateWithLifecycle()
            CarLauncherScreen(
                viewModel = viewModel,
                cards = viewModel.cards,
                layout = HomeLayout.Adaptive,
                mapContent = mapTask?.let { task ->
                    { MapTaskContainer(task, Modifier.fillMaxSize()) }
                },
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
        if (viewModel.isVehicleConnected) {
            showConnectionResult(viewModel.toggleConnection())
            return
        }
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
        showConnectionResult(viewModel.toggleConnection())
    }

    private fun showConnectionResult(message: String?) {
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG,"on start")

        viewModel.onHostStart(applicationContext)
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
    override fun onStop() {
        viewModel.onHostStop()
        Log.i(TAG,"on stop")
        super.onStop()
    }

    companion object {
        private const val TAG = "COMPOSE_MAIN"

        private const val DATABASE_RECORD_COUNT = "com.example.carlauncher.extra.RECORD_COUNT"
    }
}
