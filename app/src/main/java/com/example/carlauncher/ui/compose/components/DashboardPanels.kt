package com.example.carlauncher.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.R
import com.example.carlauncher.ui.compose.CarLauncherUiState
import com.example.carlauncher.ui.compose.designsystem.HuColors
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(HuColors.Surface), content = content)
}

@Composable
fun DashboardIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    IconButton(onClick, modifier.size(48.dp)) {
        Icon(icon, label, tint = if (active) HuColors.AccentBlue else HuColors.TextDim, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun EnergyStatus(state: CarLauncherUiState, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            EnergyMeter("Battery", if (state.showVehicleValues) "${state.batteryPercent}%" else "—", state.batteryPercent / 100f, Icons.Outlined.BatteryChargingFull, Modifier.weight(1f))
            EnergyMeter("Fuel", if (state.isDemo) "${state.fuelPercent}%" else "—", if (state.isDemo) .83f else 0f, Icons.Outlined.LocalGasStation, Modifier.weight(1f))
            Icon(Icons.Outlined.Eco, "Eco", tint = Color(0xFF42D354), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun EnergyMeter(label: String, value: String, fraction: Float, icon: ImageVector, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(17.dp), HuColors.TextDim)
            Text(label, Modifier.weight(1f).padding(start = 6.dp), color = HuColors.TextDim, fontSize = 14.sp, maxLines = 1)
            Text(value, color = Color.White, fontSize = 15.sp)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF2C3141))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFF39D349)))
        }
    }
}

@Composable
fun WeatherStatus(modifier: Modifier = Modifier, onOpenApps: () -> Unit, onWifi: () -> Unit, onBluetooth: () -> Unit) {
    val clock = LocalDashboardClock.current
    var now by remember(clock) { mutableStateOf(clock.now()) }
    LaunchedEffect(clock) { while (true) { now = clock.now(); delay(30_000) } }
    Panel(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 650.dp
            val showWeatherDescription = maxWidth > 530.dp
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.WbSunny, null, Modifier.size(28.dp).align(Alignment.TopEnd), Color(0xFFFFCB52))
                    Icon(Icons.Outlined.Cloud, null, Modifier.size(40.dp).align(Alignment.BottomStart), Color(0xFFE1E7ED))
                }
                Text("22°", color = Color.White, fontSize = 30.sp, modifier = Modifier.padding(start = 8.dp))
                if (showWeatherDescription) Column(Modifier.padding(start = 8.dp)) {
                    Text("Cloudy", color = HuColors.TextDim, fontSize = 12.sp)
                    Text("DEMO", color = HuColors.TextDim, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Text(SimpleDateFormat(if (wide) "EEEE, dd MMM yyyy" else "dd MMM", Locale.ENGLISH).format(now),
                    Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White, fontSize = if (wide) 17.sp else 14.sp, maxLines = 1)
                DashboardIcon(Icons.Outlined.Bluetooth, "Bluetooth settings", onBluetooth, active = true)
                DashboardIcon(Icons.Outlined.Wifi, "Wi-Fi settings", onWifi)
                IconButton(onOpenApps, Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF303846))) {
                    Icon(Icons.Outlined.Person, "Open apps", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
fun ClimatePanel(state: CarLauncherUiState, modifier: Modifier = Modifier, onCool: (Boolean) -> Unit,
    onAuto: () -> Unit, onPassenger: (Int) -> Unit, onDriver: (Int) -> Unit) {
    Panel(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).background(HuColors.BackgroundDark)) {
                    ClimateMode("Cool", true, state, Modifier.weight(1f), onCool)
                    ClimateMode("Warm", false, state, Modifier.weight(1f), onCool)
                }
                Spacer(Modifier.width(20.dp))
                Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (state.isAutoDrive) HuColors.AccentBlue else HuColors.AccentRed)
                    .clickable(enabled = state.isDemo, role = Role.Switch, onClick = onAuto).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.AutoMode, null, Modifier.size(22.dp), Color.White)
                    Text(if (state.isAutoDrive) "Auto On" else "Auto Drive", color = Color.White, fontSize = 16.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Temperature("Passenger", state.passengerTemp, state.isDemo, Modifier.weight(1f), onPassenger)
                Icon(Icons.Outlined.Air, "Climate fan", Modifier.size(28.dp), Color(0xFF393E4D))
                Temperature("Driver", state.driverTemp, state.isDemo, Modifier.weight(1f), onDriver)
            }
        }
    }
}

@Composable
private fun ClimateMode(label: String, cool: Boolean, state: CarLauncherUiState, modifier: Modifier, onClick: (Boolean) -> Unit) {
    Row(modifier.heightIn(min = 48.dp).background(if (state.isCool == cool) Color(0xFF151B27) else Color.Transparent)
        .selectable(state.isCool == cool, enabled = state.isDemo, role = Role.Tab, onClick = { onClick(cool) }).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(if (cool) Icons.Outlined.AcUnit else Icons.Outlined.WbSunny, null, Modifier.size(19.dp), if (cool) Color(0xFF69DBEE) else HuColors.AccentRed)
        Text(label, color = if (state.isCool == cool) Color.White else HuColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Temperature(label: String, value: Int, enabled: Boolean, modifier: Modifier, onChange: (Int) -> Unit) {
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 205.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Column {
                if (compact) Text(label, fontSize = 11.sp, color = HuColors.TextDim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) Text("$label: ", fontSize = 12.sp, color = HuColors.TextDim)
                    Text(if (enabled) "$value°" else "—", fontSize = 29.sp, color = Color.White)
                }
            }
            Column {
                IconButton({ onChange(value + 1) }, Modifier.size(48.dp, 32.dp), enabled && value < 30) {
                    Icon(Icons.Outlined.KeyboardArrowUp, "$label temperature up", tint = HuColors.TextDim, modifier = Modifier.size(20.dp))
                }
                IconButton({ onChange(value - 1) }, Modifier.size(48.dp, 32.dp), enabled && value > 16) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "$label temperature down", tint = HuColors.TextDim, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun HuCard(state: CarLauncherUiState, modifier: Modifier = Modifier, onGear: (String) -> Unit,
    onLight: () -> Unit, onLock: () -> Unit, onSettings: () -> Unit) {
    Panel(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val gaugeHeight = (maxHeight * .27f).coerceIn(72.dp, 108.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val road = Path().apply {
                        moveTo(w * .40f, h * .08f); lineTo(w * .60f, h * .08f)
                        lineTo(w * .76f, h); lineTo(w * .24f, h); close()
                    }
                    drawPath(road, Brush.verticalGradient(listOf(Color.Transparent, Color(0x22118AFF), Color.Transparent)))
                    listOf(-1, 1).forEach { side ->
                        drawLine(Brush.verticalGradient(listOf(Color.Transparent, HuColors.AccentBlue, Color.Transparent)),
                            Offset(w * (.5f + .09f * side), h * .06f), Offset(w * (.5f + .26f * side), h), 1.4.dp.toPx())
                        if (state.isLightOn) {
                            val beam = Path().apply {
                                moveTo(w * (.5f + .045f * side), h * .44f)
                                lineTo(w * (.5f + .10f * side), h * .08f)
                                lineTo(w * (.5f + .012f * side), h * .08f); close()
                            }
                            drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, Color(0x1599CFFF), Color(0xAADDEEFF)), endY = h * .44f))
                        }
                    }
                }
                Image(painterResource(R.drawable.dashboard_car_rear), "Silver vehicle", Modifier.heightIn(max = 160.dp).fillMaxHeight(.90f).fillMaxWidth(.38f).align(Alignment.BottomCenter))
                IconButton(onLight, Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape)
                    .background(if (state.isLightOn) HuColors.AccentBlue else Color(0xFF222735)), enabled = state.isDemo) {
                    Icon(Icons.Outlined.Lightbulb, "Toggle headlights", tint = Color.White)
                }
                IconButton(onLock, Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape)
                    .background(if (state.isLocked) Color(0xFF222735) else HuColors.AccentBlue), enabled = state.isDemo) {
                    Icon(if (state.isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, "Toggle door lock", tint = HuColors.TextDim)
                }
                DashboardIcon(Icons.Outlined.Settings, "Open settings", onSettings, modifier = Modifier.align(Alignment.TopEnd))
            }
            Box(Modifier.fillMaxWidth(.56f).height(gaugeHeight), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxSize()) {
                    val diameter = minOf(size.width - 12.dp.toPx(), (size.height - 8.dp.toPx()) * 2)
                    val origin = Offset((size.width - diameter) / 2, 6.dp.toPx())
                    drawArc(Color(0xFF292E3D), 180f, 180f, false, origin, Size(diameter, diameter), style = Stroke(7.dp.toPx()))
                    val sweep = 180f * (state.speedKph / 200f).coerceIn(0f, 1f)
                    if (state.showVehicleValues) drawArc(HuColors.AccentBlue, 180f, sweep, false, origin, Size(diameter, diameter), style = Stroke(7.dp.toPx()))
                    if (state.showVehicleValues) drawArc(HuColors.AccentRed, 180f + sweep, 1.5f, false, origin, Size(diameter, diameter), style = Stroke(16.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text(if (state.showVehicleValues) "${state.speedKph}" else "—", color = Color.White, fontSize = if (gaugeHeight < 90.dp) 28.sp else 33.sp, fontWeight = FontWeight.Medium)
                    Text("Km/h", color = HuColors.TextDim, fontSize = 15.sp)
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("P", "R", "N", "D").forEach { gear ->
                    Box(Modifier.size(48.dp).selectable(state.gear == gear, enabled = state.isDemo, role = Role.RadioButton, onClick = { onGear(gear) }), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(if (state.gear == gear) HuColors.AccentBlue else Color(0xFF262B3A)), contentAlignment = Alignment.Center) {
                            Text(gear, color = if (state.gear == gear) Color.White else HuColors.TextDim, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun BottomDriveModeCard(state: CarLauncherUiState, modifier: Modifier, onMode: (Boolean) -> Unit) {
    Panel(modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.dashboard_car_side), "Silver coupe side view", Modifier.weight(.42f).height(76.dp))
            Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(.58f).background(HuColors.BackgroundDark)) {
                listOf(true to "Automatic", false to "Manual").forEach { (automatic, label) ->
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).padding(2.dp)
                        .background(if (state.isAutomatic == automatic) HuColors.Surface else Color.Transparent)
                        .selectable(state.isAutomatic == automatic, enabled = state.isDemo, role = Role.Tab, onClick = { onMode(automatic) }), contentAlignment = Alignment.Center) {
                        Text(label, color = if (state.isAutomatic == automatic) Color.White else HuColors.TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(selectedIndex: Int, modifier: Modifier, onItemSelected: (Int) -> Unit) {
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Map, Icons.Outlined.NearMe, Icons.Outlined.Adjust, Icons.Outlined.LibraryMusic)
    val titles = listOf("Home", "Maps", "Navigation", "Control", "Media")
    Panel(modifier) {
        Row(Modifier.fillMaxSize()) {
            titles.forEachIndexed { index, label ->
                val color = if (selectedIndex == index) HuColors.AccentBlue else HuColors.TextDim
                Column(Modifier.weight(1f).fillMaxHeight().selectable(selectedIndex == index, role = Role.Tab, onClick = { onItemSelected(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icons[index], null, Modifier.size(26.dp), color)
                    Text(label, Modifier.padding(top = 6.dp), fontSize = 15.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
