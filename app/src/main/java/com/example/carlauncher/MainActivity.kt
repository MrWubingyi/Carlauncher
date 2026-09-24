package com.example.carlauncher

import android.Manifest
import android.app.ComponentCaller
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.carlauncher.databinding.ActivityMainBinding
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.ui.CockpitConnectionState
import com.example.carlauncher.ui.CockpitText
import com.example.carlauncher.ui.CockpitUiState
import com.example.carlauncher.ui.CockpitViewModel
import com.google.android.material.snackbar.Snackbar

/** 车载启动器主 Activity。 负责 UI 控制、TCP 连接管理以及模拟车辆数据的启动与停止。 */
open class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null // 视图绑定
    private lateinit var serviceIntent: Intent // 启动服务的 Intent
    private var vehicleService: VehicleSendService? = null
    private var serviceBindingActive: Boolean = false
    private var shouldShowWelcome: Boolean = false
    private var viewModel: CockpitViewModel? = null
    private val serviceStateListener: VehicleSendService.StateListener =
        VehicleSendService.StateListener {
            if (viewModel != null) {
                viewModel!!.refresh()
            }
        }

    /** 处理与 VehicleSendService 建立绑定的连接回调。 */
    private val serviceConnection: ServiceConnection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                // 获取 Service 实例并设置监听器
                val localBinder = binder as VehicleSendService.LocalBinder?

                vehicleService = localBinder!!.service
                // 先把 Service 交给 Repository，再注册立即回调的监听器。
                viewModel!!.attachService(vehicleService)
                vehicleService!!.setStateListener(serviceStateListener)
                Log.i(TAG, "Activity bound to VehicleSendService")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // 当 Service 进程因崩溃等原因意外终止时触发
                vehicleService = null
                viewModel!!.detachService()

                Log.w(TAG, "VehicleSendService disconnected")
            }
        }

    protected override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "MainActivity onCreate")
        super.onCreate(savedInstanceState)

        // 初始化视图绑定 (ViewBinding)
        binding = ActivityMainBinding.inflate(getLayoutInflater()!!)
        setContentView(binding!!.getRoot())
        shouldShowWelcome = (savedInstanceState == null && ThemePreferences.isWelcomeEnabled(this))
        viewModel = ViewModelProvider(this).get<CockpitViewModel>(CockpitViewModel::class.java)

        viewModel!!.uiState!!.observe(this) { uiState ->
            render(uiState)
        }
        // 处理系统栏（状态栏、导航栏）的内边距，实现沉浸式体验
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.cockpitRoot) { view, insets ->
            val bars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())

            view!!.setPadding(
                bars!!.left + dp(24),
                bars!!.top + dp(24),
                bars!!.right + dp(24),
                bars!!.bottom + dp(24),
            )

            insets
        }

        // 初始化 Service Intent
        serviceIntent = Intent(this, VehicleSendService::class.java)

        // 如果是车载系统，请求必要的车速读取权限
        requestCarSpeedPermissionIfNeeded()
        if (
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED))
        ) {

            requestPermissions(
                arrayOf<String>(Manifest.permission.POST_NOTIFICATIONS),
                100,
            )
        }

        // 配置连接/断开按钮的点击逻辑
        binding!!.connectButton.setOnClickListener {
            val state = viewModel!!.uiState!!.getValue()

            if (state == null || !state!!.isActionEnabled) {
                return@setOnClickListener
            }

            if (state!!.isStopAction()) {
                stopVehicleService()
                return@setOnClickListener
            }
            val activityId = System.identityHashCode(this)
            val viewModelId = System.identityHashCode(viewModel)

            Log.i(
                TAG,
                ("Observer owner activity=" + activityId + ", viewModel=" + viewModelId),
            )
            ContextCompat.startForegroundService(this, serviceIntent)
            bindVehicleService(Context.BIND_AUTO_CREATE)
        }

        binding!!.appsButton.setOnClickListener {
            val intent = Intent(this, AppDrawerActivity::class.java)
            startActivity(intent)
        }
        binding!!.settingsButton.setOnClickListener {
            val intent = Intent(this, FragmentLabActivity::class.java)
            startActivity(intent)
        }
        binding!!.boardButton.setOnClickListener {
            val boardIntent = Intent(this, BroadcastLabActivity::class.java)
            startActivity(boardIntent)
        }
        binding!!.databaseButton.setOnClickListener {
            val databaseIntent = Intent(this, DatabaseLabActivity::class.java)
            startActivity(databaseIntent)
        }
    }

    private fun bindVehicleService(flags: Int) {
        if (serviceBindingActive) {
            return
        }

        serviceBindingActive =
            bindService(
                serviceIntent,
                serviceConnection,
                flags,
            )

        Log.i(TAG, "bindService requested: " + serviceBindingActive)
    }

    /** 当没有车辆数据时，重置所有 UI 显示为默认或“无数据”状态。 */
    private fun renderNoData() {
        if (binding == null) {
            return
        }

        binding!!.speedText.setText("-- km/h")
        binding!!.gearText.setText("-")
        binding!!.rpmText.setText("-- rpm")
        binding!!.batteryText.setText("-- %")
        binding!!.evBatteryText.setText("-- %")
        binding!!.coolantTempText.setText("-- °C")

        binding!!.turnSignalText.setText("--")
        binding!!.parkingBrakeText.setText("--")
        binding!!.doorLockText.setText("--")

        binding!!.warningText.setText(getString(R.string.state_no_data))
        binding!!.validityText.setText(getString(R.string.state_invalid))
        binding!!.sequenceText.setText(getString(R.string.sequence_empty))

        binding!!.lastUpdateText.setText(getString(R.string.waiting_vehicle_data))
    }

    /** 将 dp 值转换为像素值。 */
    private fun dp(value: Int): Int = Math.round(value * getResources().getDisplayMetrics().density)

    /** 如果运行在 Android Automotive 系统上，检查并请求车速相关权限。 */
    private fun requestCarSpeedPermissionIfNeeded() {
        val isAutomotive =
            getPackageManager()!!.hasSystemFeature("android.hardware.type.automotive")
        if (
            (isAutomotive &&
                (checkSelfPermission(CAR_SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED))
        ) {
            requestPermissions(
                arrayOf<String>(CAR_SPEED_PERMISSION),
                CAR_PERMISSION_REQUEST_CODE,
            )
        }
    }

    /** 根据当前服务的状态（连接中、已连接、正在发送等）更新 UI 上的状态文字和按钮。 */

    /** 获取服务中最新的车辆状态并渲染到对应的 UI 控件上。 */
    private fun renderVehicleState(state: VehicleState?) {
        if (binding == null) {
            renderNoData()
            return
        }

        if (state == null) {
            renderNoData()
            return
        }

        // 更新最后更新时间/序列号
        binding!!.lastUpdateText.setText(getString(R.string.updated_sequence, state!!.sequence))

        // 更新各项车辆动态指标
        binding!!.speedText.setText(getString(R.string.speed_value, state!!.vehSpeedKph))
        binding!!.gearText.setText((state!!.gear).toString())
        binding!!.rpmText.setText(getString(R.string.rpm_value, state!!.engRpm))
        binding!!.batteryText.setText(getString(R.string.battery_value, state!!.soc))

        binding!!.turnSignalText.setText(CockpitText.state(this, (state!!.turnSignal).toString()))
        binding!!
            .parkingBrakeText
            .setText(
                getString(if (state!!.isParkingBrake) R.string.state_on else R.string.state_off)
            )
        binding!!.warningText.setText(CockpitText.state(this, (state!!.warning).toString()))
        binding!!.validityText.setText(CockpitText.state(this, (state!!.validity).toString()))
        binding!!.sequenceText.setText(getString(R.string.sequence_value, state!!.sequence))

        renderNullableFields(state)
    }

    private fun resolveConnectionBackground(state: CockpitUiState?): Int {
        when (state!!.state) {
            CockpitConnectionState.ONLINE -> return R.drawable.bg_status_online
            CockpitConnectionState.CONNECTING,
            CockpitConnectionState.RECOVERING -> return R.drawable.bg_status_connecting
            CockpitConnectionState.DISCONNECTED -> return R.drawable.bg_status_offline
            CockpitConnectionState.INVALID_DATA -> return R.drawable.bg_status_error
        }
    }

    /** 渲染可能为空的字段（可选属性）。 */
    private fun renderNullableFields(state: VehicleState?) {
        val doorLock = state!!.doorLock
        binding!!
            .doorLockText
            .setText(
                if (doorLock == null) "--"
                else getString(if (doorLock) R.string.state_locked else R.string.state_unlocked)
            )

        val coolant = state!!.engineCoolantTemp
        binding!!
            .coolantTempText
            .setText(if (coolant == null) "-- °C" else "${Math.round(coolant)} °C")

        val battery = state!!.evBatteryLevel
        binding!!.evBatteryText.setText(if (battery == null) "-- %" else "${Math.round(battery)} %")
    }

    private fun render(uiState: CockpitUiState?) {
        if (binding == null) {
            return
        }

        renderConnectionState(
            CockpitText.connection(this, uiState),
            resolveConnectionBackground(uiState),
            uiState!!.isActionEnabled,
            getString(
                if (uiState!!.isStopAction()) R.string.stop_receive else R.string.start_receive
            ),
        )

        binding!!
            .sourceStatusText
            .setText(CockpitText.state(this, (uiState!!.dataSourceStatus).toString()))
        binding!!.transportStatusText.setText(CockpitText.transport(this, uiState))

        val state = uiState!!.vehicleState
        if (state == null) {
            renderNoData()
            return
        }

        renderVehicleState(state)
    }

    private fun renderConnectionState(
        label: String?,
        backgroundRes: Int,
        buttonEnabled: Boolean,
        buttonLabel: String?,
    ) {
        binding!!.connectionStatusText.setText(label)
        binding!!.connectionStatusText.setBackgroundResource(backgroundRes)
        binding!!.connectButton.setEnabled(buttonEnabled)
        binding!!.connectButton.setText(buttonLabel)
    }

    private fun unbindVehicleService() {

        if (vehicleService != null) {
            vehicleService!!.clearStateListener(serviceStateListener)
        }

        viewModel!!.detachService()

        if (serviceBindingActive) {
            unbindService(serviceConnection)
            serviceBindingActive = false
        }

        vehicleService = null
    }

    private fun stopVehicleService() {
        unbindVehicleService()
        stopService(serviceIntent)
    }

    protected override fun onPostResume() {

        super.onPostResume()
        if (!shouldShowWelcome) {
            return
        }

        // 立即清除，防止重复进入 onPostResume 时再次显示。
        shouldShowWelcome = false

        binding!!.getRoot().post {
            Snackbar.make(
                    binding!!.getRoot(),
                    getString(R.string.welcome_message)!!,
                    Snackbar.LENGTH_LONG,
                )
                .show()

            Log.i(TAG, "Welcome Snackbar displayed")
        }
    }

    protected override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        binding = null // 释放视图绑定
        super.onDestroy()
    }

    protected override fun onResume() {
        super.onResume()
        //        startVehicleRefresh();
        Log.i(TAG, "MainActivity onResume")
    }

    protected override fun onPause() {
        Log.i(TAG, "onPause")
        super.onPause()
    }

    protected override fun onRestart() {
        Log.i(TAG, "onRestart")
        super.onRestart()
    }

    protected override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
        bindVehicleService(0)
    }

    protected override fun onStop() {
        Log.i(TAG, "onStop")
        unbindVehicleService()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        setIntent(intent)
        Log.i(TAG, "MainActivity onNewIntent")
    }

    companion object {

        private const val TAG: String = "VEHICLE_LAUNCHER"
        private const val CAR_SPEED_PERMISSION: String =
            "android.car.permission.CAR_SPEED" // 读取车速所需的权限
        private const val CAR_PERMISSION_REQUEST_CODE: Int = 100
    }
}
