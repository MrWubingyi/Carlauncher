package com.example.carlauncher.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * HuButton — 车载 HMI 通用按钮
 *
 * 支持多种风格: Primary, Danger, Ghost, Outlined
 * 带有按压动画和可选图标
 */
enum class HuButtonStyle {
    PRIMARY, DANGER, SUCCESS, GHOST, OUTLINED
}

@Composable
fun HuButton(
    text: String,
    modifier: Modifier = Modifier,
    style: HuButtonStyle = HuButtonStyle.PRIMARY,
    icon: ImageVector? = null,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgBrush = when (style) {
        HuButtonStyle.PRIMARY -> Brush.horizontalGradient(
            colors = listOf(HuColors.GradientCyanStart, HuColors.GradientCyanEnd),
        )
        HuButtonStyle.DANGER -> Brush.horizontalGradient(
            colors = listOf(HuColors.GradientRedStart, HuColors.GradientRedEnd),
        )
        HuButtonStyle.SUCCESS -> Brush.horizontalGradient(
            colors = listOf(HuColors.ButtonSuccess, HuColors.AccentTeal),
        )
        HuButtonStyle.GHOST -> Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
        )
        HuButtonStyle.OUTLINED -> Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
        )
    }

    val disabledBrush = Brush.linearGradient(
        colors = listOf(HuColors.ButtonDisabled, HuColors.ButtonDisabled),
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            !enabled -> HuColors.ButtonDisabled
            isPressed -> HuColors.AccentCyan
            style == HuButtonStyle.OUTLINED -> HuColors.CardBorder
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "borderColor",
    )

    val textColor = when {
        !enabled -> HuColors.TextDim
        style == HuButtonStyle.GHOST -> HuColors.TextAccent
        style == HuButtonStyle.OUTLINED -> HuColors.TextPrimary
        else -> HuColors.BackgroundDark
    }

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = if (enabled) bgBrush else disabledBrush,
                shape = shape,
            )
            .border(1.dp, animatedBorderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = text,
                style = HuTypography.labelLarge,
                color = textColor,
            )
        }
    }
}
