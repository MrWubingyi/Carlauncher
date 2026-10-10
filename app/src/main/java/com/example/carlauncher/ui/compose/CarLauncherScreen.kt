package com.example.carlauncher.ui.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carlauncher.ui.CockpitText
import com.example.carlauncher.ui.compose.components.*
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import kotlinx.coroutines.delay

private const val TAG = "CAR_LAUNCHER_UI"

@Composable
fun CarLauncherScreen(
    viewModel: CarLauncherViewModel = viewModel(),
    onConnectionToggle: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBroadcast: () -> Unit = {},
    onOpenDatabase: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var fullMap by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf("") }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(1_000)
                viewModel.tick()
            }
        }
    }
    BackHandler(fullMap || state.selectedNavIndex != 0) {
        if (fullMap) fullMap = false else viewModel.selectNavItem(0)
    }
    fun launch(intent: Intent) {
        focusManager.clearFocus()
        keyboard?.hide()
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Log.e(
                TAG,
                "No activity found for action=${intent.action}, component=${intent.component}",
                error,
            )
            Toast.makeText(context, "未安装可处理此操作的应用", Toast.LENGTH_SHORT).show()
        } catch (error: SecurityException) {
            Log.e(
                TAG,
                "Activity launch denied for action=${intent.action}, component=${intent.component}",
                error,
            )
            Toast.makeText(context, "系统不允许打开此功能", Toast.LENGTH_SHORT).show()
        }
    }
    fun selectTab(index: Int) {
        focusManager.clearFocus()
        keyboard?.hide()
        viewModel.selectNavItem(index)
    }

    val media: @Composable (Modifier) -> Unit = { modifier ->
        MediaCard(
            state,
            modifier,
            viewModel::togglePlayPause,
            viewModel::nextTrack,
            viewModel::previousTrack,
            viewModel::toggleFavorite,
            viewModel::toggleRepeat,
            viewModel::toggleMute,
            { showQueue = true },
            viewModel::seek,
        )
    }
    val rightContent: @Composable (Modifier) -> Unit = { modifier ->
        when (state.selectedNavIndex) {
            0,
            1 ->
                MapCard(
                    modifier,
                    bottomClearance = if (state.selectedNavIndex == 0) 182.dp else 0.dp,
                    onFullScreen = { fullMap = true },
                ) {
                    if (state.selectedNavIndex == 0)
                        media(
                            Modifier.align(Alignment.BottomCenter)
                                .padding(start = 14.dp, end = 14.dp, bottom = 36.dp)
                                .fillMaxWidth()
                                .height(146.dp)
                        )
                }
            2 ->
                NavigationPanel(
                    destination,
                    { destination = it },
                    modifier,
                    onNavigate = {
                        launch(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:0,0?q=${Uri.encode(destination.trim())}"),
                            )
                        )
                    },
                    onPreview = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        fullMap = true
                    },
                )
            3 ->
                ControlPanel(
                    state,
                    modifier,
                    onConnectionToggle,
                    viewModel::setDemo,
                    onOpenApps,
                    onOpenSettings,
                    onOpenBroadcast,
                    onOpenDatabase,
                )
            4 ->
                Panel(modifier) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AlbumArt(Modifier.size(100.dp))
                            Column(Modifier.padding(start = 20.dp)) {
                                Text("Your music", color = Color.White, fontSize = 25.sp)
                                Text("媒体演示 · 暂未接入音源", color = HuColors.TextDim, fontSize = 13.sp)
                                TextButton({
                                    launch(
                                        Intent.makeMainSelectorActivity(
                                            Intent.ACTION_MAIN,
                                            Intent.CATEGORY_APP_MUSIC,
                                        )
                                    )
                                }) {
                                    Text("打开音乐应用")
                                }
                            }
                        }
                        media(Modifier.fillMaxWidth().height(150.dp))
                        Playlist(state, viewModel::selectTrack)
                    }
                }
        }
    }
    HuTheme {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .background(Color(0xFF080B11))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp)
        ) {
            val wide = maxWidth >= 900.dp
            val header = (maxHeight * .10f).coerceIn(64.dp, 84.dp)
            val footer = (maxHeight * .145f).coerceIn(78.dp, 112.dp)
            val climate = (maxHeight * .19f).coerceIn(138.dp, 160.dp)
            val left: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnergyStatus(state, Modifier.fillMaxWidth().height(header))
                    ClimatePanel(
                        state,
                        Modifier.fillMaxWidth().height(climate),
                        viewModel::toggleCoolWarm,
                        viewModel::toggleAutoDrive,
                        viewModel::setPassengerTemp,
                        viewModel::setDriverTemp,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        HuCard(
                            state,
                            Modifier.fillMaxSize(),
                            viewModel::setGear,
                            viewModel::toggleLights,
                            viewModel::toggleLock,
                            onOpenSettings,
                        )
                        TextButton(
                            { selectTab(3) },
                            Modifier.align(Alignment.TopStart).padding(start = 10.dp),
                        ) {
                            Text(
                                if (state.isDemo) "DEMO · 演示模式"
                                else if (state.hasVehicleData) state.connectionLabel else "等待车辆数据",
                                color = HuColors.TextDim,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    BottomDriveModeCard(
                        state,
                        Modifier.fillMaxWidth().height(footer),
                        viewModel::setAutomatic,
                    )
                }
            }
            val right: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeatherStatus(
                        Modifier.fillMaxWidth().height(header),
                        onOpenApps,
                        { launch(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                        { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    )
                    rightContent(Modifier.weight(1f).fillMaxWidth())
                    BottomNavBar(
                        state.selectedNavIndex,
                        Modifier.fillMaxWidth().height(footer),
                        ::selectTab,
                    )
                }
            }
            Box(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF070A0E))
                    .padding(8.dp)
            ) {
                if (wide) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        left(Modifier.weight(.38f).fillMaxHeight())
                        right(Modifier.weight(.62f).fillMaxHeight())
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        left(Modifier.fillMaxWidth().height(700.dp))
                        right(Modifier.fillMaxWidth().height(700.dp))
                    }
                }
                if (fullMap)
                    MapCard(
                        Modifier.fillMaxSize(),
                        fullScreen = true,
                        onFullScreen = { fullMap = false },
                    )
            }
        }
        if (showQueue)
            AlertDialog(
                onDismissRequest = { showQueue = false },
                title = { Text("播放队列 · 演示") },
                text = {
                    Playlist(state) {
                        viewModel.selectTrack(it)
                        showQueue = false
                    }
                },
                confirmButton = { TextButton({ showQueue = false }) { Text("关闭") } },
            )
    }
}

@Composable
private fun NavigationPanel(
    destination: String,
    onDestination: (String) -> Unit,
    modifier: Modifier,
    onNavigate: () -> Unit,
    onPreview: () -> Unit,
) {
    Panel(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(Icons.Outlined.NearMe, null, Modifier.size(38.dp), HuColors.AccentBlue)
            Text("Where are we going?", fontSize = 27.sp, color = Color.White)
            Text("输入目的地，使用已安装的地图应用规划真实路线。", color = HuColors.TextDim)
            OutlinedTextField(
                destination,
                onDestination,
                Modifier.fillMaxWidth(),
                label = { Text("目的地") },
                singleLine = true,
            )
            Button(onNavigate, enabled = destination.isNotBlank()) { Text("开始导航") }
            HorizontalDivider(color = Color(0xFF2C3140))
            Text("离线地图预览", color = Color.White, fontSize = 18.sp)
            Text("主页地图为设计演示，无定位、路况或路线规划服务。", color = HuColors.TextDim)
            OutlinedButton(onPreview) { Text("打开演示地图") }
        }
    }
}

@Composable
private fun ControlPanel(
    state: CarLauncherUiState,
    modifier: Modifier,
    onConnection: () -> Unit,
    onDemo: (Boolean) -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    onBroadcast: () -> Unit,
    onDatabase: () -> Unit,
) {
    val context = LocalContext.current
    Panel(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Vehicle control", color = Color.White, fontSize = 26.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("演示模式", color = Color.White)
                    Text("空调、驾驶模式和车控仅用于界面演示", color = HuColors.TextDim, fontSize = 12.sp)
                }
                Switch(state.isDemo, onDemo, enabled = !state.isServiceBound)
            }
            Text(
                state.cockpit?.let { CockpitText.connection(context, it) } ?: "接收服务未启动",
                color = HuColors.AccentBlue,
            )
            Text(
                state.cockpit?.let { CockpitText.transport(context, it) } ?: "SOME/IP: STOPPED",
                color = HuColors.TextDim,
                fontSize = 12.sp,
            )
            Button(onConnection) { Text(if (state.isServiceBound) "停止接收" else "开始接收") }
            Text("实时模式只展示车辆上报数据，不发送车辆控制命令。燃油和空调暂无数据接口。", color = HuColors.TextDim, fontSize = 12.sp)
            HorizontalDivider(color = Color(0xFF2C3140))
            val vehicle = state.vehicle
            val values =
                listOf(
                    "转速" to vehicle?.let { "${it.engRpm} rpm" },
                    "转向灯" to vehicle?.turnSignal?.toString(),
                    "驻车制动" to vehicle?.let { if (it.isParkingBrake) "开启" else "关闭" },
                    "门锁" to vehicle?.doorLock?.let { if (it) "已锁" else "未锁" },
                    "安全带告警" to vehicle?.beltWarning?.let { if (it) "未系" else "正常" },
                    "近光灯 / 远光灯" to
                        vehicle?.let {
                            "${it.headlightsState ?: "—"} / ${it.highBeamLightsState ?: "—"}"
                        },
                    "冷却液温度" to vehicle?.engineCoolantTemp?.let { "$it °C" },
                    "动力电池" to vehicle?.evBatteryLevel?.let { "$it %" },
                    "告警 / 数据有效性" to vehicle?.let { "${it.warning} / ${it.validity}" },
                    "数据质量" to vehicle?.dataStatus?.toString(),
                    "序列 / 更新时间" to vehicle?.let { "${it.sequence} / ${it.timestampMs}" },
                    "数据源" to state.cockpit?.dataSourceStatus?.toString(),
                )
            values.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = HuColors.TextDim, fontSize = 13.sp)
                    Text(value ?: "—", color = Color.White, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF2C3140))
            Text("应用与实验", color = Color.White, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onApps) { Text("应用抽屉") }
                OutlinedButton(onSettings) { Text("设置 / Fragment") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onBroadcast) { Text("广播实验") }
                OutlinedButton(onDatabase) { Text("数据库实验") }
            }
        }
    }
}

@Composable
private fun Playlist(state: CarLauncherUiState, onSelect: (Int) -> Unit) {
    Column {
        CarLauncherUiState.tracks.forEachIndexed { index, track ->
            TextButton({ onSelect(index) }, Modifier.fillMaxWidth()) {
                Text(
                    "0${index + 1}",
                    color = HuColors.TextDim,
                    modifier = Modifier.padding(end = 16.dp),
                )
                Text(
                    track.title,
                    Modifier.weight(1f),
                    color = if (index == state.trackIndex) HuColors.AccentBlue else Color.White,
                    fontWeight = FontWeight.Medium,
                )
                Text(CarLauncherUiState.formatDuration(track.seconds), color = HuColors.TextDim)
            }
        }
    }
}

@Preview(name = "Automotive 1408", widthDp = 1408, heightDp = 720)
@Preview(name = "Reference proportions", widthDp = 1220, heightDp = 860)
@Composable
private fun DashboardPreview() {
    CarLauncherScreen()
}
