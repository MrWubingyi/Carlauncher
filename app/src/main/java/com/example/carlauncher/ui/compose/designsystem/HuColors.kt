package com.example.carlauncher.ui.compose.designsystem

import androidx.compose.ui.graphics.Color

/**
 * 车载 HMI 设计系统颜色定义 (复刻参考图中的暗黑极简风格)
 */
object HuColors {
    // ── 主背景 ──
    val Background = Color(0xFF0C1017)
    val BackgroundDark = Color(0xFF080B10)
    val Surface = Color(0xFF131722)
    val SurfaceVariant = Color(0xFF181F2C)
    val SurfaceElevated = Color(0xFF1F2838)

    // ── 卡片 ──
    val CardBackground = Color(0xFF131722)
    val CardBorder = Color(0xFF1D2635)

    // ── 强调色 ──
    val AccentCyan = Color(0xFF2E88FF) // Main blue in reference
    val AccentBlue = Color(0xFF2E88FF)
    val AccentTeal = Color(0xFF26C281)
    val AccentPurple = Color(0xFF7B2FF7)
    val AccentRed = Color(0xFFE83D39)
    val AccentGreen = Color(0xFF26C281)

    // ── 按钮 ──
    val ButtonPrimary = Color(0xFF2E88FF)
    val ButtonDanger = Color(0xFFE83D39)
    val ButtonSuccess = Color(0xFF26C281)
    val ButtonDisabled = Color(0xFF1B222E)

    // ── 文字 ──
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8B98A9)
    val TextAccent = Color(0xFF2E88FF)
    val TextDim = Color(0xFF505C6E)

    // ── 状态 ──
    val StatusOnline = Color(0xFF26C281)
    val StatusWarning = Color(0xFFF5A623)
    val StatusError = Color(0xFFE83D39)
    val StatusOffline = Color(0xFF505C6E)

    // ── 渐变 ──
    val GradientCyanStart = Color(0xFF2E88FF)
    val GradientCyanEnd = Color(0xFF1A60C8)
    val GradientRedStart = Color(0xFFEC483E)
    val GradientRedEnd = Color(0xFFC7281F)

    // ── 温度 ──
    val TempCool = Color(0xFF2E88FF)
    val TempWarm = Color(0xFFE83D39)

    // ── 玻璃态 ──
    val GlassBorder = Color(0x2BFFFFFF)
    val GlassOverlay = Color(0xEE111622) // High contrast overlay for media player

    // ── 速度表盘 ──
    val GaugeTrack = Color(0xFF1B2332)
    val GaugeActive = Color(0xFF2E88FF)
    val GaugeGlow = Color(0x402E88FF)

    // ── 媒体播放器 ──
    val MediaProgress = Color(0xFF2E88FF)
    val MediaProgressBg = Color(0xFF1F2939)

    // ── 档位 ──
    val GearActive = Color(0xFF2E88FF)
    val GearInactive = Color(0xFF1C2432)

    // ── 导航栏 ──
    val NavBarBg = Color(0xFF0C1017)
    val NavActive = Color(0xFF2E88FF)
    val NavInactive = Color(0xFF5A6678)
}

