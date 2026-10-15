package com.example.carlauncher.ui.home

import com.example.vehicle.control.*
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.R
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.testing.StubVehicleSendService
import com.example.carlauncher.ui.compose.CarLauncherViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class ComposeDashboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeMainActivity>()
    private fun model() = ViewModelProvider(compose.activity)[CarLauncherViewModel::class.java]

    @Test fun climateAndVehicleControls_changeStateAndSurviveRecreation() {
        compose.onNodeWithContentDescription("Passenger temperature up").performClick()
        compose.onNodeWithContentDescription("Driver temperature down").performClick()
        compose.onNodeWithText("Warm").performClick()
        compose.onNodeWithText("Manual").performClick()
        compose.onNodeWithContentDescription("Toggle headlights").performClick()
        compose.runOnIdle {
            assertEquals(21, model().uiState.value.passengerTemp)
            assertEquals(19, model().uiState.value.driverTemp)
            assertFalse(model().uiState.value.isCool)
            assertFalse(model().uiState.value.isAutomatic)
            assertFalse(model().uiState.value.isLightOn)
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("21°").assertIsDisplayed()
        compose.onNodeWithText("19°").assertIsDisplayed()
        compose.onNodeWithText("Manual").assertIsSelected()
    }

    @Test fun fullScreenMap_backReturnsToDashboard() {
        compose.onNodeWithText("Full Screen").performClick()
        compose.onNodeWithText("Exit Full Screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Full Screen").assertIsDisplayed()
    }

    @Test fun tabsShowRealPanels_andNavigationRequiresDestination() {
        compose.onNodeWithText("Navigation", useUnmergedTree = true).performClick()
        compose.onNodeWithText("开始导航").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Shanghai")
        compose.onNodeWithText("开始导航").assertIsEnabled()
        compose.onNodeWithText("Control").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Vehicle control").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Vehicle control").assertIsDisplayed()
        compose.onNodeWithText("Media", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your music").assertIsDisplayed()
    }

    @Test fun mediaQueue_wrapsAndSeekRepeatPauseWork() {
        compose.onNodeWithContentDescription("Next track").performClick()
        compose.onNodeWithText("Adventure of a Lifetime").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous track").performClick()
        compose.onNodeWithContentDescription("Favorite track").performClick()
        compose.onNodeWithContentDescription("Repeat track").performClick()
        compose.runOnIdle {
            val vm = model()
            assertTrue(vm.uiState.value.isFavorite)
            assertTrue(vm.uiState.value.isRepeat)
            vm.seek(1f)
            vm.togglePlayPause()
            vm.tick()
            assertEquals(0, vm.uiState.value.positionSeconds)
            vm.togglePlayPause()
            vm.tick()
            assertEquals(0, vm.uiState.value.positionSeconds)
            vm.previousTrack()
            assertEquals(2, vm.uiState.value.trackIndex)
            assertFalse(vm.uiState.value.isFavorite)
            vm.nextTrack()
            assertEquals(0, vm.uiState.value.trackIndex)
            assertTrue(vm.uiState.value.isFavorite)
        }
        compose.onNodeWithContentDescription("Open playlist").performClick()
        compose.onNodeWithText("播放队列 · 演示").assertIsDisplayed()
        compose.onNodeWithText("Yellow").performClick()
        compose.onNodeWithText("播放队列 · 演示").assertDoesNotExist()
        compose.onNodeWithText("Yellow").assertIsDisplayed()
    }

    @Test fun liveVehicleState_replacesDemoAndClearsOnDisconnect() {
        compose.runOnIdle {
            val vm = model()
            val source = StubVehicleSendService().withLatestState(VehicleState.Builder().setVehSpeedKph(64).setSoc(47).setGear("R").build())
            vm.attachService(source)
            assertFalse(vm.uiState.value.isDemo)
            assertEquals(64, vm.uiState.value.speedKph)
            vm.setGear("D")
            vm.setDriverTemp(28)
            assertEquals("R", vm.uiState.value.gear)
            assertEquals(20, vm.uiState.value.driverTemp)
            source.withLatestState(VehicleState.Builder().setVehSpeedKph(68).build())
            vm.refresh()
            assertEquals(68, vm.uiState.value.speedKph)
            vm.detachService()
            assertFalse(vm.uiState.value.showVehicleValues)
            assertEquals(0, vm.uiState.value.speedKph)
        }
        compose.onNodeWithText("125").assertDoesNotExist()
        compose.onNodeWithText("等待车辆数据").assertIsDisplayed()
    }

    @Test fun liveControls_requireCapabilityAndConfirmationWithoutOptimisticState() {
        val source = StubVehicleSendService().withLatestState(VehicleState.Builder().setDoorLock(true).build())
        compose.runOnIdle { model().attachService(source); model().selectNavItem(3) }
        compose.onNodeWithText("解锁车门").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            source.controls = VehicleControlSnapshot(enabledActions = setOf(VehicleAction.UNLOCK), blockedReason = "")
            model().refresh()
        }
        compose.onNodeWithText("锁车门").assertIsNotEnabled()
        compose.onNodeWithText("解锁车门").assertIsEnabled().performClick()
        compose.onNodeWithText("确认解锁车门").assertIsDisplayed()
        compose.runOnIdle { assertTrue(source.commands.isEmpty()) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(source.commands.isEmpty()) }
        compose.onNodeWithText("解锁车门").performClick()
        compose.onNodeWithText("发送请求").performClick()
        compose.runOnIdle {
            assertEquals(listOf(VehicleAction.UNLOCK), source.commands)
            assertTrue(model().uiState.value.isLocked)
            source.withLatestState(VehicleState.Builder().setDoorLock(false).build())
            source.controls = VehicleControlSnapshot(outcome = ControlOutcome.SUCCEEDED, action = VehicleAction.UNLOCK)
            model().refresh()
            assertFalse(model().uiState.value.isLocked)
            model().detachService()
        }
        compose.onNodeWithText("解锁车门").assertIsNotEnabled()
    }

    @Test fun inputBounds_rejectInvalidValues() {
        compose.runOnIdle {
            val vm = model()
            vm.setPassengerTemp(100); vm.setDriverTemp(-10)
            assertEquals(30, vm.uiState.value.passengerTemp)
            assertEquals(16, vm.uiState.value.driverTemp)
            vm.selectNavItem(-1); vm.selectTrack(99); vm.setGear("X")
            assertEquals(0, vm.uiState.value.selectedNavIndex)
            assertEquals(0, vm.uiState.value.trackIndex)
            assertEquals("D", vm.uiState.value.gear)
            vm.seek(-10f)
            assertEquals(0, vm.uiState.value.positionSeconds)
            vm.seek(Float.NaN)
            assertEquals(0, vm.uiState.value.positionSeconds)
            vm.seek(20f)
            assertEquals(261, vm.uiState.value.positionSeconds)
        }
    }

    @Test fun connectionButton_firstClickStartsService_thenStops() {
        // This test exercises Binder lifecycle; grant runtime permissions before opening
        // the service so the system permission window cannot cover the Compose hierarchy.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val activity = compose.activity
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 28 && activity.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            automation.grantRuntimePermission(activity.packageName, "android.car.permission.CAR_SPEED")
        }
        try {
            compose.onNodeWithText("Control").performClick()
            compose.onNodeWithText("连接车辆").performClick()
            compose.waitUntil(10_000) { model().uiState.value.isServiceBound }
            compose.onNodeWithText("断开车辆").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { !model().uiState.value.isServiceBound }
            compose.runOnIdle { assertFalse(model().uiState.value.showVehicleValues) }
            compose.onNodeWithText("连接车辆").assertIsDisplayed()
        } finally {
            compose.activity.stopService(Intent(compose.activity, VehicleSendService::class.java))
        }
    }
}
