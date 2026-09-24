package com.example.carlauncher.ui.compose.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 车载 HMI 排版系统
 * 优化车内可读性，清晰简洁
 */
object HuTypography {

    /** 超大数字显示 — 速度表 */
    val displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        letterSpacing = (-1).sp,
        color = HuColors.TextPrimary,
    )

    /** 大数字显示 — 温度 */
    val displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp,
        color = HuColors.TextPrimary,
    )

    /** 标题 */
    val titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.sp,
        color = HuColors.TextPrimary,
    )

    /** 中等标题 */
    val titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = 0.15.sp,
        color = HuColors.TextPrimary,
    )

    /** 小标题 */
    val titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        color = HuColors.TextSecondary,
    )

    /** 正文 */
    val bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        color = HuColors.TextPrimary,
    )

    /** 中等正文 */
    val bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        color = HuColors.TextSecondary,
    )

    /** 小正文 */
    val bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        color = HuColors.TextSecondary,
    )

    /** 标签 */
    val labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        color = HuColors.TextPrimary,
    )

    /** 中等标签 */
    val labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.25.sp,
        color = HuColors.TextPrimary,
    )

    /** 小标签 */
    val labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = HuColors.TextDim,
    )

    /** 单位文字 (km/h, rpm 等) */
    val unit = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        color = HuColors.TextSecondary,
    )
}

