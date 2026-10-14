package com.example.carlauncher.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.ui.compose.CarLauncherUiState
import com.example.carlauncher.ui.compose.designsystem.HuColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCard(state: CarLauncherUiState, modifier: Modifier = Modifier, onPlay: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onFavorite: () -> Unit,
    onRepeat: () -> Unit, onMute: () -> Unit, onQueue: () -> Unit, onSeek: (Float) -> Unit) {
    BoxWithConstraints(modifier.background(Color(0xFA272A33))) {
        val compact = maxWidth < 550.dp
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(if (compact) 152.dp else 196.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp)) {
                    DashboardIcon(Icons.Outlined.SkipPrevious, "Previous track", onPrevious)
                    IconButton(onPlay, Modifier.size(if (compact) 52.dp else 60.dp).clip(CircleShape).background(HuColors.AccentBlue)) {
                        Icon(if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (state.isPlaying) "Pause demo" else "Play demo", Modifier.size(32.dp), Color.White)
                    }
                    DashboardIcon(Icons.Outlined.SkipNext, "Next track", onNext)
                }
                Row {
                    DashboardIcon(Icons.AutoMirrored.Outlined.QueueMusic, "Open playlist", onQueue)
                    DashboardIcon(if (state.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favorite track", onFavorite, active = state.isFavorite)
                    DashboardIcon(Icons.Outlined.Repeat, "Repeat track", onRepeat, active = state.isRepeat)
                }
            }
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).fillMaxHeight(.8f).background(Color(0xFF484B54)))
            if (!compact) AlbumArt(Modifier.size(92.dp).padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.songTitle, Modifier.weight(1f), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    DashboardIcon(if (state.isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, "Mute demo", onMute)
                }
                Text("${state.artist} · 媒体演示（无音源）", color = HuColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Slider(state.mediaProgress, onSeek, Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "Seek demo track" },
                    thumb = { Spacer(Modifier.size(0.dp)) },
                    track = {
                        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                            drawRect(Color(0xFF555B68))
                            drawRect(HuColors.AccentBlue, size = size.copy(width = size.width * state.mediaProgress))
                        }
                    })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(state.currentTime, color = Color.White, fontSize = 10.sp)
                    Text(state.totalTime, color = HuColors.TextDim, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun AlbumArt(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF101515))) {
        val r = size.minDimension * .36f
        repeat(18) { index ->
            rotate(index * 20f) {
                drawCircle(Color.hsv(index * 20f, .88f, .95f).copy(alpha = .82f), r * .5f, center + Offset(0f, -r * .63f), style = Stroke(r * .30f))
            }
        }
        drawCircle(Color(0xFF111719), r * .43f)
    }
}
