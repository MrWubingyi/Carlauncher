package com.example.carlauncher.ui.compose.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import com.example.carlauncher.ui.compose.designsystem.HuColors

/** Offline illustration, intentionally separate from the external real-navigation action. */
@Composable
fun MapCard(modifier: Modifier = Modifier, fullScreen: Boolean = false, bottomClearance: Dp = 0.dp, onFullScreen: () -> Unit, content: @Composable BoxScope.() -> Unit = {}) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.background(Color(0xFF10131C)).clipToBounds()) {
        DarkMapCanvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, delta, scale, _ ->
                zoom = (zoom * scale).coerceIn(1f, 3f)
                val limitX = size.width * (zoom - 1) / 2
                val limitY = size.height * (zoom - 1) / 2
                pan = Offset((pan.x + delta.x).coerceIn(-limitX, limitX), (pan.y + delta.y).coerceIn(-limitY, limitY))
            }
        }, zoom, pan, bottomClearance)
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).background(Color(0xEC242731)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(20.dp), HuColors.AccentRed)
                Text("DLF Mall of India, Noida Sector-18", Modifier.padding(start = 8.dp), color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onFullScreen, Modifier.background(Color(0xEE242731)), shape = RoundedCornerShape(2.dp)) {
                Icon(if (fullScreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(if (fullScreen) "Exit Full Screen" else "Full Screen", color = Color.White, modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 14.dp).background(Color(0xEE202530))) {
            DashboardIcon(Icons.Outlined.Add, "Zoom in", { zoom = (zoom + .25f).coerceAtMost(3f) })
            DashboardIcon(Icons.Outlined.Remove, "Zoom out", { zoom = (zoom - .25f).coerceAtLeast(1f); pan = Offset.Zero })
            DashboardIcon(Icons.Outlined.MyLocation, "Recenter map", { zoom = 1f; pan = Offset.Zero }, active = true)
        }
        Text("DEMO MAP · 拖动 / 缩放", Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0xD90E111A)).padding(6.dp), color = HuColors.TextDim, fontSize = 10.sp, lineHeight = 12.sp)
        content()
    }
}

@Composable
fun DarkMapCanvas(modifier: Modifier = Modifier, zoom: Float = 1f, pan: Offset = Offset.Zero, bottomClearance: Dp = 0.dp) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) } }
    Canvas(modifier) {
        withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, center) }) {
            val w = size.width; val h = size.height
            fun path(points: List<Pair<Float, Float>>, closed: Boolean = false) = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first * w, p.second * h) else lineTo(p.first * w, p.second * h) }
                if (closed) close()
            }
            fun street(points: List<Pair<Float, Float>>, major: Boolean = false) {
                val road = path(points)
                val width = if (major) 7.dp.toPx() else 3.5.dp.toPx()
                drawPath(road, Color(0xFF080B12), style = Stroke(width + 3.dp.toPx(), join = StrokeJoin.Round))
                drawPath(road, if (major) Color(0xFF303541) else Color(0xFF272C38), style = Stroke(width, join = StrokeJoin.Round))
                drawPath(road, Color(0xFF414652), style = Stroke(.45.dp.toPx()))
            }
            drawRect(Color(0xFF10131C))
            // Uneven blocks follow the diagonal western roads and the north/south avenue.
            val verticals = listOf(.12f, .23f, .32f, .43f, .51f, .61f, .69f, .79f, .92f)
            verticals.forEachIndexed { i, x ->
                street(listOf(x - .05f to -.1f, x to .24f, x - .015f to .47f, x + .025f to .70f, x + .04f to 1.1f), i == 4 || i == 6)
            }
            val horizontals = listOf(.09f, .19f, .30f, .44f, .55f, .69f, .82f, .95f)
            horizontals.forEachIndexed { i, y ->
                street(listOf(-.1f to y + .17f, .20f to y, .51f to y - .015f, .77f to y - .03f, 1.1f to y + .13f), i == 2 || i == 5)
            }
            for (column in 0..7) {
                for (row in 0..6) {
                    val x = .10f + column * .108f; val y = .045f + row * .138f
                    if (column >= 6 && row in 2..4) continue
                    street(listOf(x to y, x + .067f to y + .003f, x + .066f to y + .052f, x + .004f to y + .056f))
                }
            }
            street(listOf(-.1f to .79f, .09f to .64f, .24f to .69f, .39f to .89f, .72f to .94f, 1.1f to .70f), true)
            street(listOf(.75f to -.1f, .73f to .25f, .78f to .50f, .93f to .62f, 1.1f to .85f), true)
            drawPath(path(listOf(.76f to .36f, .89f to .37f, .97f to .43f, .87f to .56f, .77f to .48f), true), Color(0xFF143D22))
            drawPath(path(listOf(.08f to .40f, .13f to .37f, .12f to .45f), true), Color(0xFF174D2C))
            drawPath(path(listOf(.17f to .37f, .21f to .38f, .20f to .46f, .16f to .45f), true), Color(0xFF163E61))
            val markerY = minOf(.59f, 1f - bottomClearance.toPx() / h - .08f).coerceAtLeast(.35f)
            val route = path(listOf(.535f to markerY, .535f to .255f, .755f to .24f, .729f to -.05f))
            drawPath(route, Color(0xFF00365F), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route, HuColors.AccentBlue, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route, Color(0xFF6CC4FF), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
            fun label(text: String, x: Float, y: Float, rotation: Float = 0f, bright: Boolean = false) {
                paint.color = if (bright) 0xFFB6BDBA.toInt() else 0xFF9298A5.toInt()
                paint.textSize = 11.dp.toPx()
                drawContext.canvas.nativeCanvas.withSave {
                    rotate(rotation, x * w, y * h)
                    drawText(text, x * w, y * h, paint)
                }
            }
            label("95th St", .33f, .175f); label("2nd Avenue", .80f, .26f, 14f)
            label("86th Street", .31f, .465f); label("3rd Ave", .61f, .552f)
            label("Lingan Road", .245f, .23f, -88f); label("93rd St", .08f, .53f, -80f)
            label("19th Avenue", .475f, .64f, 88f); label("Jawaharlal Nehru Rd", .52f, .83f, -90f)
            label("89th Street", .28f, .70f, -86f); label("13th St", .83f, .37f, 14f)
            label("SDAT Cricket", .80f, .475f, bright = true); label("Ground", .83f, .50f, bright = true)
            label("Nirmala", .30f, .58f); label("Girls HSS", .30f, .60f)
            label("86th Street", .43f, .83f); label("ASHOK NAGAR", .81f, .76f)
            val cx = .535f * w; val cy = markerY * h; val r = 17.dp.toPx()
            val arrow = Path().apply {
                moveTo(cx, cy - r); lineTo(cx + r * .76f, cy + r)
                lineTo(cx, cy + r * .62f); lineTo(cx - r * .76f, cy + r); close()
            }
            drawCircle(Color(0x22FF4949), r * 1.5f, Offset(cx, cy))
            drawPath(arrow, Color(0xFFFF4545))
        }
    }
}
