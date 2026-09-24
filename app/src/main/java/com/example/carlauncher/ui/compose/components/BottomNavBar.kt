package com.example.carlauncher.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.compose.designsystem.HuColors
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import com.example.carlauncher.ui.compose.designsystem.HuTypography

/**
 * BottomNavBar — 车载底部导航功能 Bar
 *
 * 对应用户需求："现在的功能bar也消失了"
 * 复刻参考图中的 5 大导航按键：Home | Maps | Navigation | Control | Media
 */
data class NavItem(
    val label: String,
    val icon: ImageVector,
)

val defaultNavItems = listOf(
    NavItem("Home", Icons.Outlined.Home),
    NavItem("Maps", Icons.Outlined.Map),
    NavItem("Navigation", Icons.Outlined.Explore),
    NavItem("Control", Icons.Outlined.RadioButtonChecked),
    NavItem("Media", Icons.Outlined.PermMedia),
)

@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    items: List<NavItem> = defaultNavItems,
    selectedIndex: Int = 0,
    onItemSelected: (Int) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF0C1017)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val animatedColor by animateColorAsState(
                    targetValue = if (isSelected) HuColors.AccentBlue else HuColors.NavInactive,
                    animationSpec = tween(200),
                    label = "navColor",
                )

                Column(
                    modifier = Modifier
                        .clickable { onItemSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = animatedColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = item.label,
                        style = HuTypography.labelSmall.copy(fontSize = 12.sp),
                        color = animatedColor,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 600, heightDp = 56, showBackground = true, backgroundColor = 0xFF0C1017)
@Composable
private fun BottomNavBarPreview() {
    HuTheme {
        BottomNavBar()
    }
}

