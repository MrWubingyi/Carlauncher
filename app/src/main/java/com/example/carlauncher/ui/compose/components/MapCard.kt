package com.example.carlauncher.ui.compose.components

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * MapCard — 真实导航地图与内部全屏/系统地图安全调起
 *
 * 修复：防止点击地图时 `ACTION_MAIN` + `CATEGORY_APP_MAPS` 解析到系统 Home Launcher 导致跳出到桌面
 */
@Composable
fun MapCard(
    modifier: Modifier = Modifier,
    locationName: String = "DLF Mall of India, Noida Sector-18",
    onFullScreen: () -> Unit = {},
) {
    val context = LocalContext.current

    // 安全调起地图逻辑：绝不跳出到桌面主页
    val handleMapClick = {
        var launchedExternal = false
        val pm = context.packageManager

        // 优先搜寻独立的第三方地图应用 (排除当前 launcher 和系统 launcher)
        val mapPackages = listOf(
            "com.google.android.apps.maps",
            "com.autonavi.amapauto",
            "com.baidu.BaiduMap.auto",
            "com.mapbar.android.trybest"
        )
        for (pkg in mapPackages) {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    launchedExternal = true
                    break
                }
            } catch (e: Exception) { }
        }

        // 若无独立外部地图应用，直接进入应用内部【全屏导航地图模式】，决不跳出桌面！
        if (!launchedExternal) {
            onFullScreen()
            Toast.makeText(context, "已进入全屏地图导航模式", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10141E))
            .clickable { handleMapClick() },
    ) {
        // 高密暗黑专业导航地图 Canvas
        DarkMapCanvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable { handleMapClick() }
        )

        // ── 顶部栏: 位置芯片 + Full Screen 按钮 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 位置标签
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xEE141923))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .clickable { handleMapClick() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = HuColors.AccentRed,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = locationName,
                    style = HuTypography.bodyMedium.copy(fontSize = 11.sp),
                    color = HuColors.TextPrimary,
                    maxLines = 1,
                )
            }

            // Full Screen 按钮
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xEE141923))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                    .clickable { onFullScreen() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    Icons.Filled.Fullscreen,
                    contentDescription = "Full Screen",
                    tint = HuColors.TextPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Full Screen",
                    style = HuTypography.labelMedium.copy(fontSize = 11.sp),
                    color = HuColors.TextPrimary,
                )
            }
        }

        // ── 右下角: 定位目标按钮 ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 112.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xEE141923))
                .border(1.dp, HuColors.AccentBlue, CircleShape)
                .clickable { handleMapClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MyLocation,
                contentDescription = "Target Location",
                tint = HuColors.AccentBlue,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 暗黑专业导航地图 Canvas — 密集街道网络与街道名字复刻
 */
@Composable
fun DarkMapCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val textPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#78889E")
            textSize = 8.5.dp.toPx()
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        // 1. 密集暗黑街道网络
        val minorRoadColor = Color(0xFF161D29)
        val roadColor = Color(0xFF1D2635)
        val mainRoadColor = Color(0xFF263346)
        val minorStroke = 2.dp.toPx()
        val roadStroke = 4.dp.toPx()
        val mainRoadStroke = 8.dp.toPx()

        // 密集横向街道
        val yCoords = listOf(0.10f, 0.20f, 0.32f, 0.44f, 0.58f, 0.70f, 0.85f)
        yCoords.forEachIndexed { idx, y ->
            val stroke = if (idx == 1 || idx == 3) mainRoadStroke else if (idx % 2 == 0) roadStroke else minorStroke
            val color = if (idx == 1 || idx == 3) mainRoadColor else roadColor
            drawLine(color, Offset(0f, h * y), Offset(w, h * y), strokeWidth = stroke)
        }

        // 密集纵向街道
        val xCoords = listOf(0.12f, 0.24f, 0.36f, 0.48f, 0.65f, 0.80f, 0.92f)
        xCoords.forEachIndexed { idx, x ->
            val stroke = if (idx == 4) mainRoadStroke else if (idx % 2 == 0) roadStroke else minorStroke
            val color = if (idx == 4) mainRoadColor else roadColor
            drawLine(color, Offset(w * x, 0f), Offset(w * x, h), strokeWidth = stroke)
        }

        // 2. SDAT Cricket Ground 绿地公园区
        val parkPath = Path().apply {
            moveTo(w * 0.76f, h * 0.36f)
            lineTo(w * 0.94f, h * 0.34f)
            lineTo(w * 0.96f, h * 0.54f)
            lineTo(w * 0.78f, h * 0.56f)
            close()
        }
        drawPath(path = parkPath, color = Color(0xFF173628))

        // 建筑块 (Building Blocks)
        drawRoundRect(
            color = Color(0xFF1B2433),
            topLeft = Offset(w * 0.14f, h * 0.22f),
            size = Size(w * 0.08f, h * 0.08f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawRoundRect(
            color = Color(0xFF1B2433),
            topLeft = Offset(w * 0.26f, h * 0.46f),
            size = Size(w * 0.08f, h * 0.10f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )

        // 3. 蓝绿色导航路线 (与原图路线完美吻合：沿 Jawaharlal Nehru Rd 向上 -> 右转 2nd Avenue -> 再向上)
        val routePath = Path().apply {
            moveTo(w * 0.65f, h * 0.75f)
            lineTo(w * 0.65f, h * 0.20f)
            lineTo(w * 0.80f, h * 0.20f)
            lineTo(w * 0.80f, 0f)
        }

        // 外层蓝色发光 (Glow effect)
        drawPath(
            path = routePath,
            color = Color(0x662E88FF),
            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
        )
        // 主蓝色路线
        drawPath(
            path = routePath,
            color = Color(0xFF2E88FF),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )

        // 4. 橙红色 navigation 3D 车辆位置指示箭头
        val arrowX = w * 0.65f
        val arrowY = h * 0.50f
        val arrowSize = 13.dp.toPx()

        val arrowPath = Path().apply {
            moveTo(arrowX, arrowY - arrowSize)
            lineTo(arrowX + arrowSize * 0.75f, arrowY + arrowSize)
            lineTo(arrowX, arrowY + arrowSize * 0.55f)
            lineTo(arrowX - arrowSize * 0.75f, arrowY + arrowSize)
            close()
        }
        drawPath(path = arrowPath, color = Color(0xFFFF4D4D))

        // 5. 绘制参考图中的街道名称与地点文字
        drawContext.canvas.nativeCanvas.apply {
            drawText("Lingan Road", w * 0.50f, h * 0.15f, textPaint)
            drawText("95th St", w * 0.54f, h * 0.24f, textPaint)
            drawText("2nd St", w * 0.38f, h * 0.18f, textPaint)
            drawText("93rd St", w * 0.42f, h * 0.35f, textPaint)
            drawText("86th Street", w * 0.52f, h * 0.42f, textPaint)
            drawText("89th Street", w * 0.50f, h * 0.52f, textPaint)
            drawText("19th Avenue", w * 0.60f, h * 0.48f, textPaint)
            drawText("Jawaharlal Nehru Rd", w * 0.61f, h * 0.62f, textPaint)
            drawText("3rd Ave", w * 0.68f, h * 0.46f, textPaint)
            drawText("2nd Avenue", w * 0.78f, h * 0.18f, textPaint)
            drawText("13th St", w * 0.81f, h * 0.32f, textPaint)

            // SDAT Cricket Ground 公园文字
            drawText("SDAT Cricket", w * 0.78f, h * 0.42f, textPaint)
            drawText("Ground", w * 0.78f, h * 0.47f, textPaint)
            drawText("7th St", w * 0.78f, h * 0.52f, textPaint)

            // Nirmala Girls HSS
            drawText("Nirmala", w * 0.52f, h * 0.46f, textPaint)
            drawText("Girls HSS", w * 0.52f, h * 0.49f, textPaint)
        }
    }
}

@Preview(widthDp = 550, heightDp = 450, showBackground = true, backgroundColor = 0xFF0C1017)
@Composable
private fun MapCardPreview() {
    HuTheme {
        MapCard()
    }
}
