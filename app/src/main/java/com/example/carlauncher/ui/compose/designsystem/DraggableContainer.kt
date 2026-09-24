package com.example.carlauncher.ui.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 可拖拽 + 可调整大小的容器。
 * 每个 HMI 组件都通过此容器包裹，支持触摸拖拽位置和拖拽边角改变大小。
 *
 * @param initialOffsetX 初始 X 偏移 (dp)
 * @param initialOffsetY 初始 Y 偏移 (dp)
 * @param initialWidth 初始宽度 (dp)
 * @param initialHeight 初始高度 (dp)
 * @param minWidth 最小宽度 (dp)
 * @param minHeight 最小高度 (dp)
 * @param enableDrag 是否允许拖拽移动
 * @param enableResize 是否允许调整大小
 * @param content 子内容
 */
@Composable
fun DraggableContainer(
    modifier: Modifier = Modifier,
    initialOffsetX: Dp = 0.dp,
    initialOffsetY: Dp = 0.dp,
    initialWidth: Dp = 300.dp,
    initialHeight: Dp = 200.dp,
    minWidth: Dp = 120.dp,
    minHeight: Dp = 80.dp,
    enableDrag: Boolean = true,
    enableResize: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current

    var offsetX by remember {
        mutableFloatStateOf(with(density) { initialOffsetX.toPx() })
    }
    var offsetY by remember {
        mutableFloatStateOf(with(density) { initialOffsetY.toPx() })
    }
    var width by remember {
        mutableFloatStateOf(with(density) { initialWidth.toPx() })
    }
    var height by remember {
        mutableFloatStateOf(with(density) { initialHeight.toPx() })
    }

    val minWidthPx = with(density) { minWidth.toPx() }
    val minHeightPx = with(density) { minHeight.toPx() }

    val widthDp = with(density) { width.toDp() }
    val heightDp = with(density) { height.toDp() }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(widthDp, heightDp)
    ) {
        // 主内容区域 — 拖拽移动
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(
                    color = HuColors.CardBackground,
                    shape = RoundedCornerShape(16.dp),
                )
                .border(
                    width = 1.dp,
                    color = HuColors.CardBorder,
                    shape = RoundedCornerShape(16.dp),
                )
                .then(
                    if (enableDrag) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            content = content,
        )

        // 右下角调整大小手柄
        if (enableResize) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(HuColors.AccentCyan.copy(alpha = 0.7f))
                    .border(1.dp, HuColors.AccentCyan, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newW = width + dragAmount.x
                            val newH = height + dragAmount.y
                            width = newW.coerceAtLeast(minWidthPx)
                            height = newH.coerceAtLeast(minHeightPx)
                        }
                    },
            )
        }
    }
}
