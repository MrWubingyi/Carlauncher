package com.example.carlauncher

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.Gear
import com.example.carlauncher.model.TurnSignal
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.model.WarningState
import com.example.carlauncher.testing.StubVehicleSendService
import com.example.carlauncher.ui.CockpitViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity 的 Instrumentation 集成测试：初始无数据 UI、服务快照驱动的 仪表渲染，以及快捷入口按钮的跳转目标。不点击“START RECEIVE”，避免触发
 * 真实前台 Service / TCP 副作用。
 */
@RunWith(AndroidJUnit4::class)
open class MainActivityTest {

    /**
     * 测试用 Activity 子类：Instrumentation 环境没有真实 VehicleSendService， 若允许绑定会以 null binder 回调
     * onServiceConnected 导致崩溃。 这里让 bindService 直接失败，等价于“服务未运行”的真实场景。 测试活动未声明在 manifest 中，因此用
     * ActivityController 直接驱动。
     */
    @Test
    open fun launch_showsInitialNoDataUi() {
        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
            scenario!!.onActivity { activity ->
                assertEquals("-- km/h", text(activity, R.id.speedText))
                assertEquals("-", text(activity, R.id.gearText))
                assertEquals("-- rpm", text(activity, R.id.rpmText))
                assertEquals("-- %", text(activity, R.id.batteryText))
                assertEquals("NO DATA", text(activity, R.id.warningText))
                assertEquals("INVALID", text(activity, R.id.validityText))
                assertEquals("Seq --", text(activity, R.id.sequenceText))
                assertEquals("Waiting for vehicle data", text(activity, R.id.lastUpdateText))
                assertEquals("SERVICE UNBOUND", text(activity, R.id.connectionStatusText))
                assertEquals("START RECEIVE", text(activity, R.id.connectButton))
                val connectButton = activity!!.findViewById<Button?>(R.id.connectButton)
                assertTrue(connectButton!!.isEnabled())
                assertEquals("SOME/IP: STOPPED", text(activity, R.id.transportStatusText))
            }
        }
    }

    @Test
    open fun attachedServiceWithVehicleState_rendersFullDashboard() {
        val vehicleState =
            VehicleState.Builder()
                .setVersion(1)
                .setSequence(12L)
                .setVehSpeedKph(66)
                .setEngRpm(2450)
                .setGear(Gear.D)
                .setSoc(70)
                .setTurnSignal(TurnSignal.LEFT)
                .setParkingBrake(true)
                .setWarning(WarningState.CRITICAL)
                .setValidity(DataValidity.VALID)
                .setDoorLock(true)
                .setBeltWarning(true)
                .setEngineCoolantTemp(90.5f)
                .setEvBatteryLevel(80.0f)
                .build()

        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
            scenario!!.onActivity { activity ->
                ViewModelProvider(activity!!)
                    .get<CockpitViewModel>(CockpitViewModel::class.java)
                    .attachService(
                        StubVehicleSendService()
                            .withLatestState(vehicleState)!!
                            .withSourceStatus(DataSourceStatus.CONNECTED)!!
                            .withSomeipAvailable(true, 0)!!
                            .withSomeipResponse(true, 0, 1)!!
                            .withValidity(DataValidity.VALID)
                    )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario!!.onActivity { activity ->
                assertEquals("66 km/h", text(activity, R.id.speedText))
                assertEquals("D", text(activity, R.id.gearText))
                assertEquals("2450 rpm", text(activity, R.id.rpmText))
                assertEquals("70%", text(activity, R.id.batteryText))
                assertEquals("80 %", text(activity, R.id.evBatteryText))
                assertEquals("91 °C", text(activity, R.id.coolantTempText))
                assertEquals("LEFT", text(activity, R.id.turnSignalText))
                assertEquals("ON", text(activity, R.id.parkingBrakeText))
                assertEquals("LOCKED", text(activity, R.id.doorLockText))
                assertEquals("CRITICAL", text(activity, R.id.warningText))
                assertEquals("VALID", text(activity, R.id.validityText))
                assertEquals("Seq 12", text(activity, R.id.sequenceText))
                assertEquals("Updated · Seq 12", text(activity, R.id.lastUpdateText))
                assertEquals("SOME/IP ONLINE", text(activity, R.id.connectionStatusText))
                assertEquals("STOP RECEIVE", text(activity, R.id.connectButton))
                assertEquals("CONNECTED", text(activity, R.id.sourceStatusText))
                assertEquals(
                    "SOME/IP: AVAILABLE | ONLINE | EVENT 0x8001",
                    text(activity, R.id.transportStatusText),
                )
            }
        }
    }

    @Test
    open fun attachedServiceWithNullState_rendersNoDataPlaceholders() {
        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
            scenario!!.onActivity { activity ->
                ViewModelProvider(activity!!)
                    .get<CockpitViewModel>(CockpitViewModel::class.java)
                    .attachService(
                        StubVehicleSendService()
                            .withLatestState(null)!!
                            .withSourceStatus(DataSourceStatus.CONNECTED)!!
                            .withSomeipAvailable(true, 0)!!
                            .withSomeipResponse(true, 0, 1)!!
                            .withValidity(DataValidity.VALID)
                    )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario!!.onActivity { activity ->
                assertEquals("SOME/IP ONLINE", text(activity, R.id.connectionStatusText))
                assertEquals("-- km/h", text(activity, R.id.speedText))
                assertEquals("NO DATA", text(activity, R.id.warningText))
            }
        }
    }

    private fun text(activity: MainActivity?, viewId: Int): String? {
        val view = activity!!.findViewById<TextView?>(viewId)
        return view!!.getText().toString()
    }

    @Test
    open fun invalidSnapshot_displaysRawValuesAlongsideInvalidity() {
        val state =
            VehicleState.Builder()
                .setVehSpeedKph(255)
                .setEngRpm(9999)
                .setSoc(150)
                .setEvBatteryLevel(150f)
                .setEngineCoolantTemp(-999f)
                .setValidity(DataValidity.INVALID_SPEED)
                .build()
        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
            scenario!!.onActivity { activity ->
                ViewModelProvider(activity!!)
                    .get<CockpitViewModel>(CockpitViewModel::class.java)
                    .attachService(
                        StubVehicleSendService()
                            .withLatestState(state)!!
                            .withSomeipAvailable(true, 0)!!
                            .withSomeipResponse(true, 0, 1)!!
                            .withValidity(DataValidity.INVALID_SPEED)
                    )
                assertEquals("255 km/h", text(activity, R.id.speedText))
                assertEquals("9999 rpm", text(activity, R.id.rpmText))
                assertEquals("100%", text(activity, R.id.batteryText))
                assertEquals("150 %", text(activity, R.id.evBatteryText))
                assertEquals("-999 °C", text(activity, R.id.coolantTempText))
                assertEquals("INVALID_SPEED", text(activity, R.id.validityText))
                assertEquals(
                    "SOME/IP ONLINE / INVALID DATA",
                    text(activity, R.id.connectionStatusText),
                )
            }
        }
    }

    @Test
    open fun nextSnapshotWithMissingOptionals_clearsPreviouslyDisplayedValues() {
        val service =
            StubVehicleSendService()
                .withLatestState(
                    VehicleState.Builder()
                        .setDoorLock(true)
                        .setEngineCoolantTemp(90f)
                        .setEvBatteryLevel(70f)
                        .build()
                )
        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
            scenario!!.onActivity { activity ->
                val model =
                    ViewModelProvider(activity!!)
                        .get<CockpitViewModel>(CockpitViewModel::class.java)
                model.attachService(service)
                assertEquals("LOCKED", text(activity, R.id.doorLockText))
                assertEquals("90 °C", text(activity, R.id.coolantTempText))
                assertEquals("70 %", text(activity, R.id.evBatteryText))
                service!!.withLatestState(VehicleState.Builder().setDoorLock(null).build())
                model.refresh()
                assertEquals("--", text(activity, R.id.doorLockText))
                assertEquals("-- °C", text(activity, R.id.coolantTempText))
                assertEquals("-- %", text(activity, R.id.evBatteryText))
            }
        }
    }

    @Test
    open fun someipFaultsRemainVisibleUntilEventRecovery() {
        val service = StubVehicleSendService().withSomeipAvailable(true, 0)
        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
            scenario!!.onActivity { activity ->
                val model =
                    ViewModelProvider(activity!!)
                        .get<CockpitViewModel>(CockpitViewModel::class.java)
                model.attachService(service)
                assertEquals("SOME/IP WAITING EVENT", text(activity, R.id.connectionStatusText))
                assertEquals("STOP RECEIVE", text(activity, R.id.connectButton))
                assertTrue(activity!!.findViewById<View?>(R.id.connectButton)!!.isEnabled())

                service!!.withSomeipResponse(false, 1, 10)
                model.refresh()
                assertEquals("SOME/IP RESPONSE_ERROR", text(activity, R.id.connectionStatusText))
                assertTrue(text(activity, R.id.transportStatusText)!!.contains("RESPONSE_ERROR"))

                service!!.checkSomeipTimeout(3010)
                model.refresh()
                assertEquals("SOME/IP EVENT TIMEOUT", text(activity, R.id.connectionStatusText))
                assertEquals("STOP RECEIVE", text(activity, R.id.connectButton))

                service!!.withSomeipResponse(true, 0, 3020)
                model.refresh()
                assertEquals("SOME/IP ONLINE", text(activity, R.id.connectionStatusText))
            }
        }
    }
}
