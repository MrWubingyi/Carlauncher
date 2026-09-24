package com.example.carlauncher.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carlauncher.ui.compose.components.BottomDriveModeCard
import com.example.carlauncher.ui.compose.components.BottomNavBar
import com.example.carlauncher.ui.compose.components.ClimatePanel
import com.example.carlauncher.ui.compose.components.DarkMapCanvas
import com.example.carlauncher.ui.compose.components.HuCard
import com.example.carlauncher.ui.compose.components.MapCard
import com.example.carlauncher.ui.compose.components.MediaCard
import com.example.carlauncher.ui.compose.components.StatusBar
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * CarLauncherScreen — 车载主界面 (1:1 完美复刻参考图)
 */
@Composable
fun CarLauncherScreen(
    viewModel: CarLauncherViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var isFullScreenMap by remember { mutableStateOf(false) }

    HuTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080A0F))
                // 避开 AAOS 车机系统栏
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 42.dp, start = 6.dp, end = 6.dp, top = 2.dp),
        ) {
            // 暗黑沉浸式车载主窗口边框容器 (复刻原图中黑色圆角外壳)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HuColors.Background)
                    .border(1.5.dp, Color(0xFF161C26), RoundedCornerShape(14.dp))
                    .padding(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // ════════════ 1. 左侧面板 (40% 宽度): 电池油量 + 空调 + 仪表卡片 + 驱动模式卡片 ════════════
                    Column(
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 左顶部状态栏 (Battery 72% | Fuel 83% | Eco Leaf)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                        ) {
                            StatusBar(
                                modifier = Modifier.fillMaxSize(),
                                batteryPercent = state.batteryPercent,
                                fuelPercent = state.fuelPercent,
                                showRightSide = false,
                            )
                        }

                        // 左侧主卡片容器 (空调 + Porsche 3D 跑车与仪表)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(HuColors.Surface)
                                .border(1.dp, HuColors.CardBorder, RoundedCornerShape(12.dp)),
                        ) {
                            // 空调控制 (Cool/Warm, Auto Drive, Passenger/Driver 20° 滚动滑动动画调温)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp),
                            ) {
                                ClimatePanel(
                                    passengerTemp = state.passengerTemp,
                                    driverTemp = state.driverTemp,
                                    isCool = state.isCool,
                                    isAutoDrive = state.isAutoDrive,
                                    onPassengerTempChange = { viewModel.setPassengerTemp(it) },
                                    onDriverTempChange = { viewModel.setDriverTemp(it) },
                                    onCoolWarmToggle = { viewModel.toggleCoolWarm(it) },
                                    onAutoDriveToggle = { viewModel.toggleAutoDrive() },
                                )
                            }

                            // 水平分割线
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(HuColors.CardBorder),
                            )

                            // Porsche 3D 跑车透视图 + 125 Km/h 速度表 + P R N D 档位
                            Box(modifier = Modifier.weight(1f)) {
                                HuCard(
                                    speedKph = state.speedKph,
                                    gear = state.gear,
                                    onGearSelect = { viewModel.setGear(it) },
                                )
                            }
                        }

                        // 左底部独立卡片: Porsche 跑车侧影 + Automatic/Manual 模式切换
                        BottomDriveModeCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            isAutomatic = state.isAutomatic,
                            onModeSelect = { viewModel.setAutomatic(it) },
                        )
                    }

                    // ════════════ 2. 右侧面板 (60% 宽度): 天气日期 + 地图 + 媒体 + 底部导航 Bar ════════════
                    Column(
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 右顶部状态栏 (Weather 22° Cloudy | Date | Bluetooth | Wifi | User Profile Avatar)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                        ) {
                            StatusBar(
                                modifier = Modifier.fillMaxSize(),
                                temperature = state.temperature,
                                weatherDesc = state.weatherDesc,
                                showLeftSide = false,
                            )
                        }

                        // 右侧主内容区域: 地图 + 悬浮媒体播放器
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(HuColors.Surface)
                                .border(1.dp, HuColors.CardBorder, RoundedCornerShape(12.dp)),
                        ) {
                            // MapCard: 安全全屏展开，不跳出桌面主页
                            MapCard(
                                locationName = state.locationName,
                                onFullScreen = { isFullScreenMap = true },
                            )

                            // 悬浮在地图下方的玻璃态媒体播放器卡片
                            MediaCard(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp)
                                    .fillMaxWidth()
                                    .height(100.dp),
                                songTitle = state.songTitle,
                                artist = state.artist,
                                progress = state.mediaProgress,
                                currentTime = state.currentTime,
                                totalTime = state.totalTime,
                                isPlaying = state.isPlaying,
                                onPlayPause = { viewModel.togglePlayPause() },
                                onNext = { viewModel.nextTrack() },
                                onPrevious = { viewModel.previousTrack() },
                            )
                        }

                        // 复刻参考图底部的 5 大功能导航 Bar
                        BottomNavBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            selectedIndex = state.selectedNavIndex,
                            onItemSelected = {
                                viewModel.selectNavItem(it)
                                if (it == 1) { // 点击 Maps 标签直接进入全屏地图模式
                                    isFullScreenMap = true
                                }
                            },
                        )
                    }
                }

                // ════════════ 3. 应用内部全屏地图导航模式 (决不跳出桌面) ════════════
                AnimatedVisibility(
                    visible = isFullScreenMap,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F141F)),
                    ) {
                        // 全屏地图底层 Canvas
                        DarkMapCanvas(
                            modifier = Modifier.fillMaxSize()
                        )

                        // 全屏顶部导航栏: 返回按钮 + 搜索栏 + 路径指引
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 返回按钮
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xEE161D2A))
                                    .border(1.dp, HuColors.AccentBlue, RoundedCornerShape(20.dp))
                                    .clickable { isFullScreenMap = false }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "返回主页",
                                    style = HuTypography.labelLarge.copy(fontSize = 13.sp),
                                    color = Color.White,
                                )
                            }

                            // 导航搜索提示框
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xEE161D2A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = HuColors.TextDim,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = state.locationName,
                                    style = HuTypography.bodyMedium.copy(fontSize = 12.sp),
                                    color = HuColors.TextPrimary,
                                )
                            }

                            // 退出全屏按钮
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xEE161D2A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                                    .clickable { isFullScreenMap = false }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.FullscreenExit,
                                    contentDescription = "Exit Full Screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "退出全屏",
                                    style = HuTypography.labelMedium.copy(fontSize = 12.sp),
                                    color = Color.White,
                                )
                            }
                        }

                        // 左上角实时导航提示卡片
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 60.dp, start = 12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xEE1A2332))
                                .border(1.dp, HuColors.AccentBlue, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Navigation,
                                contentDescription = "Turn",
                                tint = HuColors.AccentBlue,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = "200m 后右转进入 2nd Avenue",
                                    style = HuTypography.labelLarge.copy(fontSize = 12.sp),
                                    color = Color.White,
                                )
                                Text(
                                    text = "预计 12 分钟 • 4.8 公里",
                                    style = HuTypography.bodySmall.copy(fontSize = 10.sp),
                                    color = HuColors.TextDim,
                                )
                            }
                        }

                        // 右下角迷你媒体控制组件
                        MediaCard(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(width = 380.dp, height = 90.dp),
                            songTitle = state.songTitle,
                            artist = state.artist,
                            progress = state.mediaProgress,
                            currentTime = state.currentTime,
                            totalTime = state.totalTime,
                            isPlaying = state.isPlaying,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.nextTrack() },
                            onPrevious = { viewModel.previousTrack() },
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 1024, heightDp = 600, showBackground = true, backgroundColor = 0xFF080B10)
@Composable
private fun CarLauncherScreenPreview() {
    CarLauncherScreen()
}
