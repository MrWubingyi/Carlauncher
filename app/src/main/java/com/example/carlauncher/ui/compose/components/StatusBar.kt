package com.example.carlauncher.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * StatusBar — 顶部状态栏
 *
 * 左侧: 电池电量、油量、Eco 叶子图标
 * 右侧: 天气、日期、蓝牙/WiFi、用户头像
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    batteryPercent: Int = 72,
    fuelPercent: Int = 83,
    temperature: Int = 22,
    weatherDesc: String = "Cloudy",
    dateString: String = "Friday, 22 Dec 2023",
    showLeftSide: Boolean = true,
    showRightSide: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = if (showLeftSide && !showRightSide) {
            Arrangement.SpaceBetween
        } else if (!showLeftSide && showRightSide) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── 左侧: 电池 + 油量 + Eco ──
        if (showLeftSide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    StatusItem(
                        icon = { Icon(Icons.Outlined.BatteryChargingFull, null, tint = HuColors.TextDim, modifier = Modifier.size(16.dp)) },
                        label = "Battery",
                        value = "$batteryPercent%",
                        progress = batteryPercent / 100f,
                        progressColor = HuColors.StatusOnline,
                    )

                    StatusItem(
                        icon = { Icon(Icons.Outlined.LocalGasStation, null, tint = HuColors.TextDim, modifier = Modifier.size(16.dp)) },
                        label = "Fuel",
                        value = "$fuelPercent%",
                        progress = fuelPercent / 100f,
                        progressColor = HuColors.StatusOnline,
                    )
                }

                // 右侧 Eco 环保图标
                Icon(
                    Icons.Filled.Eco,
                    contentDescription = "Eco",
                    tint = HuColors.StatusOnline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── 右侧: 天气 + 日期 + 连接 + 头像 ──
        if (showRightSide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 天气
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.WbCloudy,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "${temperature}°",
                        style = HuTypography.titleLarge.copy(fontSize = 20.sp),
                        color = HuColors.TextPrimary,
                    )
                    Text(
                        text = weatherDesc,
                        style = HuTypography.bodyMedium,
                        color = HuColors.TextSecondary,
                    )
                }

                // 日期
                Text(
                    text = dateString,
                    style = HuTypography.bodyMedium,
                    color = HuColors.TextPrimary,
                )

                // 蓝牙 / WiFi / 头像
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Filled.Bluetooth, null, tint = HuColors.AccentBlue, modifier = Modifier.size(18.dp))
                    Icon(Icons.Filled.SignalWifi4Bar, null, tint = HuColors.TextSecondary, modifier = Modifier.size(18.dp))

                    // 用户头像 — 带有蓝色圆环边框
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(HuColors.SurfaceVariant)
                            .border(1.5.dp, HuColors.AccentBlue, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, null, tint = HuColors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    progress: Float,
    progressColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = label, style = HuTypography.labelMedium, color = HuColors.TextSecondary)
                Text(text = value, style = HuTypography.labelLarge, color = HuColors.TextPrimary)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(70.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = progressColor,
                trackColor = HuColors.GaugeTrack,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Preview(widthDp = 1024, heightDp = 48, showBackground = true, backgroundColor = 0xFF0C1017)
@Composable
private fun StatusBarPreview() {
    HuTheme {
        StatusBar()
    }
}

