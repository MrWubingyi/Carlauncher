package com.example.carlauncher.someip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.MainActivity
import com.example.carlauncher.R
import com.example.carlauncher.service.VehicleSendService
import java.util.concurrent.atomic.AtomicLong
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/* Opt-in real SD/Event -> foreground Service -> repository -> visible TextView test. */
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
        val config = java.io.File(context!!.getFilesDir(), "vsomeip/vsomeip-client.json")
        val previous =
            if (config.exists()) java.nio.file.Files.readAllBytes(config.toPath()) else null
        java.nio.file.Files.deleteIfExists(config.toPath())
        try {
            ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
                scenario!!.onActivity { a ->
                    a!!.findViewById<View?>(R.id.connectButton)!!.performClick()
                }
                val first = AtomicLong(-1)
                val latest = AtomicLong(-1)
                val deadline = android.os.SystemClock.elapsedRealtime() + 45000
                while (
                    android.os.SystemClock.elapsedRealtime() < deadline &&
                        latest.get() < first.get() + 10
                ) {
                    scenario!!.onActivity { a ->
                        val speed =
                            (a!!.findViewById<View?>(R.id.speedText) as TextView)
                                .getText()
                                .toString()
                        if (speed!!.startsWith("--")) return@onActivity
                        try {
                            for (field in MainActivity::class.java.getDeclaredFields()!!) {
                                if (field!!.getType() != VehicleSendService::class.java) continue
                                field!!.setAccessible(true)
                                val service = field!!.get(a) as VehicleSendService?
                                if (service == null || service!!.getLatestVehicleState() == null)
                                    return@onActivity
                                val seq = service!!.getLatestVehicleState()!!.sequence
                                assertEquals(
                                    SomeipConnectionMonitor.State.ONLINE,
                                    service!!.someipStatus!!.state,
                                )
                                assertEquals(
                                    "${service!!.getLatestVehicleState()!!.vehSpeedKph} km/h",
                                    speed,
                                )
                                if (first.get() < 0) first.set(seq)
                                latest.set(seq)
                                android.util.Log.i(
                                    "EVENT_UI_PROBE",
                                    "UI seq=" + seq + " speed=" + speed,
                                )
                            }
                        } catch (e: IllegalAccessException) {
                            throw AssertionError(e)
                        }
                    }
                    Thread.sleep(100)
                }
                assertTrue(
                    "At least ten changing remote snapshots must reach actual UI",
                    first.get() >= 0 && latest.get() >= first.get() + 10,
                )
                scenario!!.onActivity { a ->
                    a!!.findViewById<View?>(R.id.connectButton)!!.performClick()
                }
            }
        } finally {
            context!!.stopService(Intent(context, VehicleSendService::class.java))
            if (previous != null) java.nio.file.Files.write(config.toPath(), previous)
        }
    }
}
