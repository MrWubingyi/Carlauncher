package com.example.carlauncher.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * ClimatePanel — 空调与自动驾驶控制
 *
 * 对应需求："空调温度调节应该上下滑动时能看到滑动动画"
 * 使用 AnimatedContent + slideInVertically / slideOutVertically 实现平滑视差滚轮调温动画
 */
@Composable
fun ClimatePanel(
    modifier: Modifier = Modifier,
    passengerTemp: Int = 20,
    driverTemp: Int = 20,
    isCool: Boolean = true,
    isAutoDrive: Boolean = false,
    onPassengerTempChange: (Int) -> Unit = {},
    onDriverTempChange: (Int) -> Unit = {},
    onCoolWarmToggle: (Boolean) -> Unit = {},
    onAutoDriveToggle: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── 顶部控制条: Cool/Warm + Auto Drive ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cool / Warm 切换
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HuColors.BackgroundDark)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ToggleChip(
                    text = "Cool",
                    icon = Icons.Filled.AcUnit,
                    isSelected = isCool,
                    activeColor = HuColors.AccentBlue,
                    onClick = { onCoolWarmToggle(true) },
                )
                ToggleChip(
                    text = "Warm",
                    icon = Icons.Filled.LocalFireDepartment,
                    isSelected = !isCool,
                    activeColor = HuColors.AccentRed,
                    onClick = { onCoolWarmToggle(false) },
                )
            }

            // Auto Drive 按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                HuColors.GradientRedStart,
                                HuColors.GradientRedEnd,
                            )
                        )
                    )
                    .clickable { onAutoDriveToggle() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "A",
                            style = HuTypography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                        )
                    }
                    Text(
                        text = "Auto Drive",
                        style = HuTypography.labelLarge.copy(fontSize = 13.sp),
                        color = Color.White,
                    )
                }
            }
        }

        // ── 温度手势上下滑动控制区域 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Passenger Temp (含平滑滚轮滑动动画)
            TemperatureSwipePicker(
                label = "Passenger:",
                temp = passengerTemp,
                onTempChange = onPassengerTempChange,
            )

            // 风扇图标 (居中)
            Icon(
                Icons.Filled.Air,
                contentDescription = "Fan",
                tint = HuColors.TextDim,
                modifier = Modifier.size(20.dp),
            )

            // Driver Temp (含平滑滚轮滑动动画)
            TemperatureSwipePicker(
                label = "Driver:",
                temp = driverTemp,
                onTempChange = onDriverTempChange,
            )
        }
    }
}

/**
 * 支持手势上下滑动调温与视差垂直滚动动画组件 TemperatureSwipePicker
 */
@Composable
private fun TemperatureSwipePicker(
    label: String,
    temp: Int,
    onTempChange: (Int) -> Unit,
) {
    var totalDrag by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 12.dp.value // 触发步进的滑动距离

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = HuTypography.bodyMedium.copy(fontSize = 13.sp),
            color = HuColors.TextSecondary,
        )

        // 数字滑动触摸响应区域
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(HuColors.SurfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                .pointerInput(temp) {
                    detectVerticalDragGestures(
                        onDragEnd = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                            if (totalDrag <= -dragThreshold) { // 向上滑动 -> 增加温度
                                onTempChange(temp + 1)
                                totalDrag = 0f
                            } else if (totalDrag >= dragThreshold) { // 向下滑动 -> 减少温度
                                onTempChange(temp - 1)
                                totalDrag = 0f
                            }
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 垂直视差滑动动画数字框
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = temp,
                        transitionSpec = {
                            if (targetState > initialState) {
                                // 升温：新数字从下方滑入，旧数字向上滑出
                                (slideInVertically(animationSpec = tween(220)) { height -> height } + fadeIn(tween(220))) togetherWith
                                        (slideOutVertically(animationSpec = tween(220)) { height -> -height } + fadeOut(tween(220)))
                            } else {
                                // 降温：新数字从上方滑入，旧数字向下滑出
                                (slideInVertically(animationSpec = tween(220)) { height -> -height } + fadeIn(tween(220))) togetherWith
                                        (slideOutVertically(animationSpec = tween(220)) { height -> height } + fadeOut(tween(220)))
                            }
                        },
                        label = "TempScrollAnim",
                    ) { targetTemp ->
                        Text(
                            text = "${targetTemp}°",
                            style = HuTypography.displayMedium.copy(fontSize = 24.sp),
                            color = HuColors.TextPrimary,
                        )
                    }
                }

                // 上下滑动微调箭头指示
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Increase",
                        tint = HuColors.AccentBlue,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onTempChange(temp + 1) },
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Decrease",
                        tint = HuColors.AccentBlue,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onTempChange(temp - 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) HuColors.SurfaceVariant else Color.Transparent,
        animationSpec = tween(200),
        label = "chipBg",
    )
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(animatedBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                icon,
                null,
                tint = if (isSelected) activeColor else HuColors.TextDim,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = HuTypography.labelMedium.copy(fontSize = 12.sp),
                color = if (isSelected) HuColors.TextPrimary else HuColors.TextDim,
            )
        }
    }
}

@Preview(widthDp = 420, heightDp = 100, showBackground = true, backgroundColor = 0xFF131722)
@Composable
private fun ClimatePanelPreview() {
    HuTheme {
        ClimatePanel()
    }
}
