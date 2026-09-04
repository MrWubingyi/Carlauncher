package com.example.carlauncher;

import android.widget.Button;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.Gear;
import com.example.carlauncher.model.TurnSignal;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.model.WarningState;
import com.example.carlauncher.service.TcpConnectionState;
import com.example.carlauncher.testing.StubVehicleSendService;
import com.example.carlauncher.ui.CockpitViewModel;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MainActivity 的 Instrumentation 集成测试：初始无数据 UI、服务快照驱动的
 * 仪表渲染，以及快捷入口按钮的跳转目标。不点击“START SEND”，避免触发
 * 真实前台 Service / TCP 副作用。
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    /**
     * 测试用 Activity 子类：Instrumentation 环境没有真实 VehicleSendService，
     * 若允许绑定会以 null binder 回调 onServiceConnected 导致崩溃。
     * 这里让 bindService 直接失败，等价于“服务未运行”的真实场景。
     * 测试活动未声明在 manifest 中，因此用 ActivityController 直接驱动。
     */
    @Test
    public void launch_showsInitialNoDataUi() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("-- km/h", text(activity, R.id.speedText));
                assertEquals("-", text(activity, R.id.gearText));
                assertEquals("-- rpm", text(activity, R.id.rpmText));
                assertEquals("-- %", text(activity, R.id.batteryText));
                assertEquals("NO DATA", text(activity, R.id.warningText));
                assertEquals("INVALID", text(activity, R.id.validityText));
                assertEquals("Seq --", text(activity, R.id.sequenceText));
                assertEquals("Waiting for vehicle data", text(activity, R.id.lastUpdateText));
                assertEquals("SERVICE UNBOUND", text(activity, R.id.connectionStatusText));
                assertEquals("START SEND", text(activity, R.id.connectButton));
                Button connectButton = activity.findViewById(R.id.connectButton);
                assertTrue(connectButton.isEnabled());
                assertEquals("Transport: OFFLINE", text(activity, R.id.transportStatusText));
            });
        }
    }

    @Test
    public void attachedServiceWithVehicleState_rendersFullDashboard() {
        VehicleState vehicleState = new VehicleState.Builder()
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
                .build();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> new ViewModelProvider(activity)
                    .get(CockpitViewModel.class)
                    .attachService(new StubVehicleSendService()
                            .withLatestState(vehicleState)
                            .withSourceStatus(DataSourceStatus.CONNECTED)
                            .withTcpState(TcpConnectionState.ONLINE)
                            .withValidity(DataValidity.VALID)));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("66 km/h", text(activity, R.id.speedText));
                assertEquals("D", text(activity, R.id.gearText));
                assertEquals("2450 rpm", text(activity, R.id.rpmText));
                assertEquals("70%", text(activity, R.id.batteryText));
                assertEquals("80 %", text(activity, R.id.evBatteryText));
                assertEquals("91 °C", text(activity, R.id.coolantTempText));
                assertEquals("LEFT", text(activity, R.id.turnSignalText));
                assertEquals("ON", text(activity, R.id.parkingBrakeText));
                assertEquals("LOCKED", text(activity, R.id.doorLockText));
                assertEquals("CRITICAL", text(activity, R.id.warningText));
                assertEquals("VALID", text(activity, R.id.validityText));
                assertEquals("Seq 12", text(activity, R.id.sequenceText));
                assertEquals("Updated · Seq 12", text(activity, R.id.lastUpdateText));
                assertEquals("SENDING", text(activity, R.id.connectionStatusText));
                assertEquals("STOP SEND", text(activity, R.id.connectButton));
                assertEquals("CONNECTED", text(activity, R.id.sourceStatusText));
                assertEquals("Transport: ONLINE", text(activity, R.id.transportStatusText));
            });
        }
    }

    @Test
    public void attachedServiceWithNullState_rendersNoDataPlaceholders() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> new ViewModelProvider(activity)
                    .get(CockpitViewModel.class)
                    .attachService(new StubVehicleSendService()
                            .withLatestState(null)
                            .withSourceStatus(DataSourceStatus.CONNECTED)
                            .withTcpState(TcpConnectionState.ONLINE)
                            .withValidity(DataValidity.VALID)));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("SENDING", text(activity, R.id.connectionStatusText));
                assertEquals("-- km/h", text(activity, R.id.speedText));
                assertEquals("NO DATA", text(activity, R.id.warningText));
            });
        }
    }

    private static String text(MainActivity activity, int viewId) {
        TextView view = activity.findViewById(viewId);
        return view.getText().toString();
    }
}


