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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * MediaCard — 地图上方悬浮的深色玻璃态媒体播放器卡片
 *
 * 左侧: 快退/播放(蓝色亮圈)/快进 + 播放列表/爱心/循环
 * 中间: 细分割线
 * 右侧: 炫彩 Coldplay 专辑封面、歌名、歌手名、音量图标、蓝色进度条与时间
 */
@Composable
fun MediaCard(
    modifier: Modifier = Modifier,
    songTitle: String = "Hymn For The Weekend",
    artist: String = "Coldplay",
    currentTime: String = "02:41",
    totalTime: String = "04:21",
    progress: Float = 0.63f,
    isPlaying: Boolean = true,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE111622))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── 左半部分: 播放控制与功能按钮 ──
            Column(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 上一首 / 播放(亮蓝大圆) / 下一首
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.FastRewind,
                        contentDescription = "Previous",
                        tint = HuColors.TextPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onPrevious() },
                    )

                    // 播放/暂停大圆按钮 (亮蓝色)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(HuColors.AccentBlue)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Icon(
                        Icons.Filled.FastForward,
                        contentDescription = "Next",
                        tint = HuColors.TextPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onNext() },
                    )
                }

                // 辅助功能行: 播放列表 | 爱心 | 循环
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, tint = HuColors.TextDim, modifier = Modifier.size(16.dp))
                    Icon(Icons.Filled.FavoriteBorder, null, tint = HuColors.TextDim, modifier = Modifier.size(16.dp))
                    Icon(Icons.Filled.Repeat, null, tint = HuColors.TextDim, modifier = Modifier.size(16.dp))
                }
            }

            // 垂直分割线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.85f)
                    .background(Color(0x22FFFFFF))
            )

            Spacer(Modifier.width(14.dp))

            // ── 右半部分: 专辑封面 + 歌曲信息 + 进度条 ──
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Coldplay 曼陀罗炫彩专辑封面 Canvas
                        MosaicAlbumArtCanvas(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )

                        Column {
                            Text(
                                text = songTitle,
                                style = HuTypography.titleMedium.copy(fontSize = 15.sp),
                                color = HuColors.TextPrimary,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = artist,
                                style = HuTypography.bodySmall,
                                color = HuColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }

                    // 右上角音量图标
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = HuColors.TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // 进度条与时间
                Column {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = HuColors.AccentBlue,
                        trackColor = HuColors.MediaProgressBg,
                        strokeCap = StrokeCap.Round,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = currentTime, style = HuTypography.labelSmall.copy(fontSize = 11.sp), color = HuColors.TextDim)
                        Text(text = totalTime, style = HuTypography.labelSmall.copy(fontSize = 11.sp), color = HuColors.TextDim)
                    }
                }
            }
        }
    }
}

/**
 * Coldplay Hymn for the weekend 风格炫彩曼陀罗专辑封面 Canvas
 */
@Composable
private fun MosaicAlbumArtCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 黑色基底
        drawRect(color = Color(0xFF0F0E17))

        // 放射几何花纹
        val colors = listOf(
            Color(0xFFFF2A55),
            Color(0xFFFFB300),
            Color(0xFF00E5FF),
            Color(0xFF76FF03),
            Color(0xFFD500F9),
        )

        val center = Offset(w / 2, h / 2)
        val radius = w / 2

        repeat(8) { i ->
            val angle = i * (360f / 8)
            val color = colors[i % colors.size]
            drawCircle(
                color = color.copy(alpha = 0.6f),
                radius = radius * 0.45f,
                center = Offset(
                    center.x + (radius * 0.35f * kotlin.math.cos(Math.toRadians(angle.toDouble()))).toFloat(),
                    center.y + (radius * 0.35f * kotlin.math.sin(Math.toRadians(angle.toDouble()))).toFloat()
                ),
            )
        }

        // 中心五彩花朵
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFFF2A55), Color(0xFF00E5FF)),
            ),
            radius = radius * 0.35f,
            center = center,
        )
    }
}

@Preview(widthDp = 520, heightDp = 130, showBackground = true, backgroundColor = 0xFF0C1017)
@Composable
private fun MediaCardPreview() {
    HuTheme {
        MediaCard()
    }
}

