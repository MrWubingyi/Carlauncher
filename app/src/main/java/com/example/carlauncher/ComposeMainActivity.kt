package com.example.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.carlauncher.ui.compose.CarLauncherScreen
import com.example.carlauncher.ui.compose.CarLauncherViewModel

/**
 * ComposeMainActivity — 基于 Compose 的车载主界面 Activity
 *
 * 替代原有 XML ViewBinding 的 MainActivity，使用 Compose 渲染整个仪表盘界面。
 * 保留与 VehicleSendService 的服务绑定能力。
 */
class ComposeMainActivity : ComponentActivity() {

    private val viewModel: CarLauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarLauncherScreen(viewModel = viewModel)
        }
    }
}
