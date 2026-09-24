package com.example.carlauncher.ui.compose.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

private val HuDarkColorScheme = darkColorScheme(
    primary = HuColors.AccentCyan,
    onPrimary = HuColors.BackgroundDark,
    primaryContainer = HuColors.AccentBlue,
    onPrimaryContainer = HuColors.TextPrimary,
    secondary = HuColors.AccentTeal,
    onSecondary = HuColors.BackgroundDark,
    tertiary = HuColors.AccentPurple,
    background = HuColors.Background,
    onBackground = HuColors.TextPrimary,
    surface = HuColors.Surface,
    onSurface = HuColors.TextPrimary,
    surfaceVariant = HuColors.SurfaceVariant,
    onSurfaceVariant = HuColors.TextSecondary,
    outline = HuColors.CardBorder,
    error = HuColors.StatusError,
    onError = HuColors.TextPrimary,
)

/**
 * 车载 HMI Compose 主题
 */
@Composable

fun HuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HuDarkColorScheme,
        content = content,
    )
}
