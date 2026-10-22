package com.zqautomotive.launcher.service

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zqautomotive.launcher.ui.compose.CarLauncherViewModel
import com.zqautomotive.launcher.ui.home.ComposeMainActivity
import com.zqautomotive.vehicle.someip.adapter.SomeipConnectionMonitor
import java.util.concurrent.atomic.AtomicLong
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/* Opt-in real SD/Event -> foreground Service -> repository -> Compose state test. */
@RunWith(AndroidJUnit4::class)
open class VehicleEventProbeTest {
    @Test
    @Throws(Exception::class)
    open fun remoteEventsUpdateActivity() {
        Assume.assumeTrue(
            "true" == InstrumentationRegistry.getArguments().getString("someipEvents")
        )
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .grantRuntimePermission(
                context!!.getPackageName(),
                Manifest.permission.POST_NOTIFICATIONS,
            )
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .grantRuntimePermission(
                context!!.getPackageName(),
                "android.car.permission.CAR_SPEED",
            )
        context!!.stopService(Intent(context, VehicleSendService::class.java))
        // Use current production SD config, not a saved legacy static Method probe config.
        val config = java.io.File(context!!.getFilesDir(), "vsomeip/vsomeip-client.override.json")
        val previous =
            if (config.exists()) java.nio.file.Files.readAllBytes(config.toPath()) else null
        java.nio.file.Files.deleteIfExists(config.toPath())
        try {
            ActivityScenario.launch<ComposeMainActivity>(ComposeMainActivity::class.java).use {
                scenario ->
                scenario.onActivity(::toggleConnection)
                val first = AtomicLong(-1)
                val latest = AtomicLong(-1)
                val deadline = android.os.SystemClock.elapsedRealtime() + 45000
                while (
                    android.os.SystemClock.elapsedRealtime() < deadline &&
                        latest.get() < first.get() + 10
                ) {
                    scenario.onActivity { activity ->
                        val service = boundService(activity) ?: return@onActivity
                        val vehicle = service.getLatestVehicleState() ?: return@onActivity
                        val model = ViewModelProvider(activity)[CarLauncherViewModel::class.java]
                        model.refresh()
                        assertEquals(
                            SomeipConnectionMonitor.State.ONLINE,
                            service.someipStatus!!.state,
                        )
                        assertEquals(vehicle.vehSpeedKph, model.uiState.value.speedKph)
                        val seq = vehicle.sequence
                        if (first.get() < 0) first.set(seq)
                        latest.set(seq)
                        android.util.Log.i(
                            "EVENT_UI_PROBE",
                            "Compose seq=$seq speed=${model.uiState.value.speedKph} km/h",
                        )
                    }
                    Thread.sleep(100)
                }
                assertTrue(
                    "At least ten changing remote snapshots must reach Compose state",
                    first.get() >= 0 && latest.get() >= first.get() + 10,
                )
                scenario.onActivity(::toggleConnection)
            }
        } finally {
            context!!.stopService(Intent(context, VehicleSendService::class.java))
            if (previous != null) java.nio.file.Files.write(config.toPath(), previous)
        }
    }

    private fun toggleConnection(activity: ComposeMainActivity) {
        val method = ComposeMainActivity::class.java.getDeclaredMethod("toggleConnection")
        method.isAccessible = true
        method.invoke(activity)
    }

    private fun boundService(activity: ComposeMainActivity): VehicleSendService? {
        val field = ComposeMainActivity::class.java.getDeclaredField("vehicleService")
        field.isAccessible = true
        return field.get(activity) as VehicleSendService?
    }
}
