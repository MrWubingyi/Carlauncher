package com.example.carlauncher.ui.screenshot

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.dropbox.differ.SimpleImageComparator
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.ui.compose.CarLauncherScreen
import com.example.carlauncher.ui.compose.CarLauncherViewModel
import com.example.carlauncher.ui.compose.components.DashboardClock
import com.example.carlauncher.ui.compose.components.LocalDashboardClock
import com.example.vehicle.someip.adapter.SomeipConnectionMonitor
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Same fixture runs against the pre-refactor and current production screen/ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w1408dp-h792dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class HomeScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private lateinit var model: CarLauncherViewModel

    @Before fun showHome() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            model = CarLauncherViewModel()
            store.put("screenshot", model)
        }
        val fixedDate = GregorianCalendar(2026, Calendar.OCTOBER, 21, 10, 0, 0).apply {
            set(Calendar.MILLISECOND, 0)
        }.time
        compose.setContent {
            CompositionLocalProvider(LocalDashboardClock provides DashboardClock { fixedDate }) {
                CarLauncherScreen(viewModel = model)
            }
        }
        settle()
    }

    @After fun release() {
        compose.runOnUiThread { store.clear() }
    }

    @Test fun homeDemo() {
        compose.onNodeWithText("125").assertIsDisplayed()
        capture("home_demo")
    }

    @Test fun mapFullscreen() {
        compose.onNodeWithText("Full Screen").performClick()
        settle()
        compose.onNodeWithText("Exit Full Screen").assertIsDisplayed()
        capture("map_fullscreen")
    }

    @Test fun mediaQueue() {
        compose.onNodeWithContentDescription("Open playlist").performClick()
        settle()
        compose.onNodeWithText("播放队列 · 演示").assertIsDisplayed()
        // Screen capture includes the dialog window and the dimmed dashboard behind it.
        capture("media_queue")
    }

    @Test fun homeConnected() {
        connectFixture()
        compose.onNodeWithText("64").assertIsDisplayed()
        capture("home_connected")
    }

    @Test fun vehicleConnected() {
        connectFixture()
        compose.onNodeWithText("Control").performClick()
        settle()
        compose.onNodeWithText("断开车辆").assertIsDisplayed()
        capture("vehicle_connected")
    }

    @Test fun vehicleDisconnected() {
        connectFixture()
        compose.runOnUiThread { model.detachService() }
        compose.onNodeWithText("Control").performClick()
        settle()
        compose.onNodeWithText("连接车辆").assertIsDisplayed()
        capture("vehicle_disconnected")
    }

    private fun connectFixture() {
        compose.runOnUiThread { model.attachService(ScreenshotVehicleService()) }
        settle()
    }

    private fun settle() {
        // Drain UI snapshot notifications before advancing the paused frame clock.
        // Otherwise a click may enqueue recomposition only after the final frame.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        captureScreenRoboImage(
            filePath = "${System.getProperty("home.screenshot.directory", "src/test/screenshots/home")}/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = 0f,
                    imageComparator = SimpleImageComparator(maxDistance = 0f),
                ),
            ),
        )
    }

    /** Snapshot reader only: never calls Service.onCreate, binds, or starts native transport. */
    private class ScreenshotVehicleService : VehicleSendService() {
        private val monitor = SomeipConnectionMonitor().apply {
            start()
            onAvailability(true, 1_000)
            onResponse(true, 0, 1_100)
        }
        private val vehicle = VehicleState.Builder()
            .setVehSpeedKph(64).setEngRpm(2_400).setSoc(47).setGear("D")
            .setDoorLock(true).setTimestampMs(1_100).build()
        override val someipStatus get() = monitor.snapshot()
        override var dataValidity: DataValidity? = DataValidity.VALID
        override fun getLatestVehicleState() = vehicle
        override fun getSourceStatus() = DataSourceStatus.CONNECTED
    }
}
