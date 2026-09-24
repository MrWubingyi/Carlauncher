package com.example.carlauncher.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * HuCard — 车辆驾驶控制与仪表盘卡片 (复刻参考图)
 *
 * 包含：
 * 1. 顶部 Porsche 911 3D 后透视渲染与前向探照灯束
 * 2. 悬浮灯光按钮 (左) 与 悬浮锁车按钮 (右) 以及 Settings 按钮 (右上)
 * 3. 125 Km/h 速度表盘与 P R N D 档位指示器
 */
@Composable
fun HuCard(
    modifier: Modifier = Modifier,
    speedKph: Int = 125,
    maxSpeed: Int = 240,
    gear: String = "D",
    isLightOn: Boolean = true,
    isLocked: Boolean = true,
    onGearSelect: (String) -> Unit = {},
    onLightToggle: () -> Unit = {},
    onLockToggle: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
    ) {
        // ── 右上角 Settings 按钮 ──
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = HuColors.TextDim,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clickable { },
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── 1. Porsche 911 3D 后视图 + 前向探照光束 + 左右悬浮按钮 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f),
                contentAlignment = Alignment.Center,
            ) {
                // Porsche 911 3D 跑车 Canvas
                Porsche3DVisualizerCanvas(
                    modifier = Modifier.fillMaxSize(),
                )

                // 左侧悬浮灯光按钮 (亮蓝色)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLightOn) HuColors.AccentBlue else HuColors.SurfaceVariant)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        .clickable { onLightToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = "Lights",
                        tint = if (isLightOn) Color.White else HuColors.TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // 右侧悬浮锁车按钮 (深色)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLocked) HuColors.SurfaceVariant else HuColors.AccentBlue)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        .clickable { onLockToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Lock",
                        tint = if (isLocked) HuColors.TextDim else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // ── 2. 125 Km/h 速度仪表盘 ──
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center,
            ) {
                SpeedGauge(
                    speed = speedKph,
                    maxSpeed = maxSpeed,
                    modifier = Modifier.fillMaxSize(),
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = "$speedKph",
                        style = HuTypography.displayLarge.copy(fontSize = 38.sp),
                        color = HuColors.TextPrimary,
                    )
                    Text(
                        text = "Km/h",
                        style = HuTypography.bodySmall.copy(fontSize = 11.sp),
                        color = HuColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── 3. 档位指示器: P R N [D] ──
            GearIndicator(
                currentGear = gear,
                onGearSelect = onGearSelect,
            )
        }
    }
}

/**
 * 底部跑车侧影与 Automatic/Manual 模式卡片 (独立卡片复刻原图)
 */
@Composable
fun BottomDriveModeCard(
    modifier: Modifier = Modifier,
    isAutomatic: Boolean = true,
    onModeSelect: (Boolean) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HuColors.Surface)
            .border(1.dp, HuColors.CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 高精度银色 Porsche 911 侧影 (带黄色刹车卡钳)
        PorscheSideProfileCanvas(
            modifier = Modifier
                .width(110.dp)
                .height(34.dp),
        )

        // Automatic / Manual 切换
        DriveModeTabs(
            isAutomatic = isAutomatic,
            onModeSelect = onModeSelect,
        )
    }
}

/**
 * 3D Porsche 911 跑车后透视图 Canvas (复刻原图)
 */
@Composable
private fun Porsche3DVisualizerCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val carCenterX = w * 0.5f
        val carCenterY = h * 0.65f
        val carW = 90.dp.toPx()
        val carH = 70.dp.toPx()

        // 1. 绘制透视发光车道线 (/ \)
        val laneLeftTop = Offset(carCenterX - 20.dp.toPx(), 4.dp.toPx())
        val laneRightTop = Offset(carCenterX + 20.dp.toPx(), 4.dp.toPx())
        val laneLeftBottom = Offset(carCenterX - 75.dp.toPx(), h)
        val laneRightBottom = Offset(carCenterX + 75.dp.toPx(), h)

        // 发光探照光束 (从车前延伸至顶部的强光路)
        val beamPath = Path().apply {
            moveTo(carCenterX - 18.dp.toPx(), carCenterY - 15.dp.toPx())
            lineTo(laneLeftTop.x, laneLeftTop.y)
            lineTo(laneRightTop.x, laneRightTop.y)
            lineTo(carCenterX + 18.dp.toPx(), carCenterY - 15.dp.toPx())
            close()
        }

        drawPath(
            path = beamPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE2F1FF), // 探照灯核心白光
                    Color(0x882E88FF), // 蓝色高光
                    Color(0x112E88FF), // 渐隐
                ),
                startY = laneLeftTop.y,
                endY = carCenterY - 15.dp.toPx(),
            ),
        )

        // 蓝车道边线
        drawLine(
            start = laneLeftBottom,
            end = laneLeftTop,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF2E88FF), Color(0x442E88FF)),
            ),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            start = laneRightBottom,
            end = laneRightTop,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF2E88FF), Color(0x442E88FF)),
            ),
            strokeWidth = 2.dp.toPx(),
        )

        // 2. Porsche 911 3D 跑车车身渲染
        val left = carCenterX - carW / 2
        val top = carCenterY - carH / 2

        // A. 跑车车身整体轮廓
        val bodyPath = Path().apply {
            // 顶棚 (Roof Curve)
            moveTo(left + carW * 0.32f, top + carH * 0.12f)
            cubicTo(
                left + carW * 0.42f, top + carH * 0.02f,
                left + carW * 0.58f, top + carH * 0.02f,
                left + carW * 0.68f, top + carH * 0.12f
            )
            // 宽体肩线与后轮拱 (Wide 911 Haunches)
            cubicTo(
                left + carW * 0.85f, top + carH * 0.30f,
                left + carW * 0.98f, top + carH * 0.45f,
                left + carW, top + carH * 0.70f
            )
            // 后保险杠底部
            lineTo(left + carW * 0.94f, top + carH * 0.92f)
            lineTo(left + carW * 0.06f, top + carH * 0.92f)
            lineTo(left, top + carH * 0.70f)
            // 左侧肩线
            cubicTo(
                left + carW * 0.02f, top + carH * 0.45f,
                left + carW * 0.15f, top + carH * 0.30f,
                left + carW * 0.32f, top + carH * 0.12f
            )
            close()
        }

        // 车身金属质感渐变
        drawPath(
            path = bodyPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE2E8F0), // 金属银高光
                    Color(0xFFA0ACC0), // 银灰色
                    Color(0xFF4A5568), // 阴影
                    Color(0xFF1E2633), // 底部暗色
                ),
                startY = top,
                endY = top + carH,
            ),
        )

        // B. 后挡风玻璃 (Sloping Rear Glass with Defroster Lines)
        val glassPath = Path().apply {
            moveTo(left + carW * 0.34f, top + carH * 0.15f)
            cubicTo(
                left + carW * 0.44f, top + carH * 0.06f,
                left + carW * 0.56f, top + carH * 0.06f,
                left + carW * 0.66f, top + carH * 0.15f
            )
            lineTo(left + carW * 0.78f, top + carH * 0.48f)
            lineTo(left + carW * 0.22f, top + carH * 0.48f)
            close()
        }
        drawPath(
            path = glassPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF121824), Color(0xFF070A10)),
            ),
        )

        // C. 贯穿式鲜红 LED 尾灯带 (Porsche Signature LED Light Bar)
        drawRoundRect(
            color = Color(0xFFFF1A24),
            topLeft = Offset(left + carW * 0.08f, top + carH * 0.60f),
            size = Size(carW * 0.84f, 3.5.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        // 尾灯发光 Halo Glow Effect
        drawRoundRect(
            color = Color(0x66FF1A24),
            topLeft = Offset(left + carW * 0.05f, top + carH * 0.58f),
            size = Size(carW * 0.90f, 6.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )

        // D. 牌照框与中网 (License Plate Recess)
        drawRoundRect(
            color = Color(0xFF0F141E),
            topLeft = Offset(left + carW * 0.35f, top + carH * 0.70f),
            size = Size(carW * 0.30f, 10.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )

        // E. 双边共四出排气管 (Quad Exhaust Tips)
        drawCircle(
            color = Color(0xFFCCCCCC),
            radius = 3.dp.toPx(),
            center = Offset(left + carW * 0.22f, top + carH * 0.88f),
        )
        drawCircle(
            color = Color(0xFFCCCCCC),
            radius = 3.dp.toPx(),
            center = Offset(left + carW * 0.28f, top + carH * 0.88f),
        )
        drawCircle(
            color = Color(0xFFCCCCCC),
            radius = 3.dp.toPx(),
            center = Offset(left + carW * 0.72f, top + carH * 0.88f),
        )
        drawCircle(
            color = Color(0xFFCCCCCC),
            radius = 3.dp.toPx(),
            center = Offset(left + carW * 0.78f, top + carH * 0.88f),
        )
    }
}

/**
 * 高精度 Porsche 911 侧影 Canvas 绘制 (带黄色刹车卡钳)
 */
@Composable
private fun PorscheSideProfileCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 车身侧轮廓 (Porsche 911 Classic Side Profile)
        val carBody = Path().apply {
            moveTo(0f, h * 0.72f) // 前保险杠下沿
            cubicTo(w * 0.08f, h * 0.55f, w * 0.18f, h * 0.42f, w * 0.32f, h * 0.30f) // 前机盖与 A 柱
            cubicTo(w * 0.45f, h * 0.15f, w * 0.65f, h * 0.15f, w * 0.82f, h * 0.45f) // Teardrop 倾斜车顶与 C 柱
            cubicTo(w * 0.90f, h * 0.52f, w * 0.96f, h * 0.60f, w, h * 0.72f) // 后尾翼与后保险杠
            lineTo(w, h * 0.82f)
            lineTo(0f, h * 0.82f)
            close()
        }

        // 金属银渐变车身
        drawPath(
            path = carBody,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE2E8F0), // 顶部金属光泽
                    Color(0xFF94A3B8), // 车侧
                    Color(0xFF334155), // 下包围阴影
                ),
            ),
        )

        // 车窗玻璃 (Black Window Trim)
        val windowPath = Path().apply {
            moveTo(w * 0.35f, h * 0.33f)
            cubicTo(w * 0.45f, h * 0.22f, w * 0.62f, h * 0.22f, w * 0.75f, h * 0.45f)
            lineTo(w * 0.35f, h * 0.45f)
            close()
        }
        drawPath(path = windowPath, color = Color(0xFF0F172A))

        // 前后轮毂 (Dark Alloy Wheels with Yellow Calipers)
        val wheelRadius = h * 0.24f
        val frontWheelCenter = Offset(w * 0.22f, h * 0.82f)
        val rearWheelCenter = Offset(w * 0.78f, h * 0.82f)

        // 轮胎橡胶
        drawCircle(color = Color(0xFF0F141C), radius = wheelRadius, center = frontWheelCenter)
        drawCircle(color = Color(0xFF0F141C), radius = wheelRadius, center = rearWheelCenter)

        // 轮毂内圈
        drawCircle(color = Color(0xFF334155), radius = wheelRadius * 0.75f, center = frontWheelCenter)
        drawCircle(color = Color(0xFF334155), radius = wheelRadius * 0.75f, center = rearWheelCenter)

        // 黄色卡钳 (Yellow Porsche Brake Calipers)
        drawCircle(color = Color(0xFFFFB300), radius = wheelRadius * 0.45f, center = frontWheelCenter)
        drawCircle(color = Color(0xFFFFB300), radius = wheelRadius * 0.45f, center = rearWheelCenter)

        // 轮毂中心盖
        drawCircle(color = Color(0xFF0F141C), radius = wheelRadius * 0.25f, center = frontWheelCenter)
        drawCircle(color = Color(0xFF0F141C), radius = wheelRadius * 0.25f, center = rearWheelCenter)
    }
}

/**
 * 弧形速度仪表盘
 */
@Composable
private fun SpeedGauge(
    speed: Int,
    maxSpeed: Int,
    modifier: Modifier = Modifier,
) {
    val sweepAngle = 220f
    val startAngle = 160f
    val fraction = (speed.toFloat() / maxSpeed).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800),
        label = "speedAnim",
    )

    Canvas(modifier = modifier) {
        val padding = 10.dp.toPx()
        val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
        val topLeft = Offset(padding, padding)

        // 1. 背景轨道
        drawArc(
            color = HuColors.GaugeTrack,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
        )

        // 2. 蓝色激活弧
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(HuColors.AccentBlue, Color(0xFF2E88FF), HuColors.AccentBlue),
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle * animatedFraction,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
        )

        // 3. 红色针尖
        val redTipSweep = 8f
        val currentEndAngle = startAngle + sweepAngle * animatedFraction
        drawArc(
            color = HuColors.AccentRed,
            startAngle = (currentEndAngle - redTipSweep).coerceAtLeast(startAngle),
            sweepAngle = redTipSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * 档位指示器: P R N [D]
 */
@Composable
private fun GearIndicator(
    currentGear: String,
    onGearSelect: (String) -> Unit,
) {
    val gears = listOf("P", "R", "N", "D")
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        gears.forEach { g ->
            val isActive = g == currentGear
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isActive) HuColors.AccentBlue else HuColors.GearInactive)
                    .clickable { onGearSelect(g) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = g,
                    style = HuTypography.labelLarge.copy(fontSize = 13.sp),
                    color = if (isActive) Color.White else HuColors.TextDim,
                )
            }
        }
    }
}

/**
 * Automatic / Manual 驾驶模式切换
 */
@Composable
private fun DriveModeTabs(
    isAutomatic: Boolean,
    onModeSelect: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(HuColors.BackgroundDark)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (isAutomatic) HuColors.SurfaceVariant else Color.Transparent)
                .clickable { onModeSelect(true) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Automatic",
                style = HuTypography.labelMedium.copy(fontSize = 11.sp),
                color = if (isAutomatic) HuColors.TextPrimary else HuColors.TextDim,
            )
        }
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (!isAutomatic) HuColors.SurfaceVariant else Color.Transparent)
                .clickable { onModeSelect(false) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Manual",
                style = HuTypography.labelMedium.copy(fontSize = 11.sp),
                color = if (!isAutomatic) HuColors.TextPrimary else HuColors.TextDim,
            )
        }
    }
}

@Preview(widthDp = 380, heightDp = 340, showBackground = true, backgroundColor = 0xFF0C1017)
@Composable
private fun HuCardPreview() {
    HuTheme {
        HuCard()
    }
}
