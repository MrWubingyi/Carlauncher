package com.example.carlauncher.someip

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.MainActivity
import com.example.carlauncher.R
import com.example.carlauncher.service.VehicleSendService
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/* Explicit LAN acceptance probe. Exercises the real Activity, Service, JNI and peer. */
@RunWith(AndroidJUnit4::class)
open class VehicleServiceLifecycleProbeTest {
    private val instrumentation: Instrumentation? = InstrumentationRegistry.getInstrumentation()
    private val context: Context? = instrumentation!!.getTargetContext()

    @Test
    @Throws(Exception::class)
    open fun backgroundRebindAndRestartReleaseEachNativeClientOnce() {
        val peer = InstrumentationRegistry.getArguments().getString("someipPeer")
        val local = InstrumentationRegistry.getArguments().getString("someipLocal")
        Assume.assumeTrue(
            "Explicit someipServiceLifecycle=true and LAN addresses required",
            ("true" == InstrumentationRegistry.getArguments().getString("someipServiceLifecycle") &&
                peer != null &&
                local != null),
        )
        val config = File(context!!.getFilesDir(), "vsomeip/vsomeip-client.json")
        val original = if (config.exists()) Files.readAllBytes(config.toPath()) else null
        val serviceIntent = Intent(context, VehicleSendService::class.java)
        // Refuse to disturb a service the user already started.
        for (info in
            context!!
                .getSystemService<ActivityManager>(ActivityManager::class.java)!!
                .getRunningServices(100)!!) {
            assertNotEquals(
                "probe requires a stopped VehicleSendService",
                VehicleSendService::class.java.getName(),
                info!!.service!!.getClassName(),
            )
        }
        assertFalse("probe requires an idle native client", VsomeipClient.isAvailable)
        // These normal runtime prompts otherwise cover the Activity and may outlive
        // its task on AAOS. The opt-in probe runs in Gradle's disposable app install.
        for (permission in
            arrayOf<String>(
                "android.permission.POST_NOTIFICATIONS",
                "android.car.permission.CAR_SPEED",
            )) {
            if (context!!.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                instrumentation!!
                    .getUiAutomation()
                    .grantRuntimePermission(context!!.getPackageName(), permission)
            }
        }
        Files.createDirectories(config.toPath().getParent())
        val json =
            JSONObject()
                .put("unicast", local)
                .put("logging", JSONObject().put("level", "info").put("console", "true"))
                .put(
                    "applications",
                    JSONArray()
                        .put(JSONObject().put("name", "soc-vehicle-client").put("id", "0x5566")),
                )!!
                .put("routing", "soc-vehicle-client")
                .put(
                    "services",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("service", "0x1111")
                                .put("instance", "0x2222")
                                .put("unicast", peer)
                                .put("unreliable", "30509")
                        ),
                )!!
                .put(
                    "service-discovery",
                    JSONObject()
                        .put("enable", "true")
                        .put("multicast", "224.244.224.245")
                        .put("port", "30490")
                        .put("protocol", "udp")
                        .put("ttl", "3"),
                )
        Files.write(config.toPath(), json!!.toString(2).toByteArray(StandardCharsets.UTF_8))
        val releaseBaseline = occurrences(logcat(), "SOMEIP_RELEASED id=")
        val nativeStopBaseline = occurrences(logcat(), "vsomeip app stopped")
        var previous: VehicleSendService? = null
        try {
            for (cycle in 0..1) {
                val activity =
                    instrumentation!!.startActivitySync(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ) as MainActivity?
                try {
                    onActivity(activity) { value ->
                        value!!.findViewById<View?>(R.id.connectButton)!!.performClick()
                    }
                    await("Activity binds to a real online Service") {
                        val service = boundService(activity)
                        service != null &&
                            service!!.someipStatus!!.state == SomeipConnectionMonitor.State.ONLINE
                    }
                    val service = boundService(activity)
                    assertNotSame("restart must create a new Service", previous, service)
                    previous = service
                    val id = System.identityHashCode(service)
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=foreground id=" + id)
                    onActivity(activity) { value ->
                        assertEquals(
                            "STOP RECEIVE",
                            (value!!.findViewById<View?>(R.id.connectButton) as TextView)
                                .getText()
                                .toString(),
                        )
                    }

                    shell("input keyevent KEYCODE_HOME")
                    await("Home must stop the Activity and release its binding") {
                        val background = AtomicReference<Boolean?>(false)
                        instrumentation!!.runOnMainSync {
                            background.set(
                                !activity!!
                                    .lifecycle
                                    .currentState
                                    .isAtLeast(Lifecycle.State.STARTED)
                            )
                        }
                        background.get()!! && boundService(activity) == null
                    }
                    val sequence = service!!.getLatestVehicleState()!!.sequence
                    val responsesBefore = occurrences(logcat(), "VEHICLE_EVENT seq=")
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=background id=" + id)
                    await("remote vehicle Events continue after Activity unbind") {
                        (service!!.getLatestVehicleState()!!.sequence >= sequence + 5 &&
                            occurrences(logcat(), "VEHICLE_EVENT seq=") >= responsesBefore + 5)
                    }
                    assertEquals(
                        SomeipConnectionMonitor.State.ONLINE,
                        service!!.someipStatus!!.state,
                    )

                    // Bring the real task forward after Home; ActivityScenario's internal
                    // lifecycle transition alone does not foreground an AAOS task.
                    context!!.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(
                                (Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                    )
                    await("foreground rebind must retain Service identity") {
                        boundService(activity) === service
                    }
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=rebound id=" + id)
                    onActivity(activity) { value ->
                        assertEquals(
                            "STOP RECEIVE",
                            (value!!.findViewById<View?>(R.id.connectButton) as TextView)
                                .getText()
                                .toString(),
                        )
                        value!!.findViewById<View?>(R.id.connectButton)!!.performClick()
                    }
                    await("native release completes") {
                        logcat()!!.contains("SOMEIP_RELEASED id=" + id)
                    }
                    assertNull(boundService(activity))
                    assertEquals(
                        SomeipConnectionMonitor.State.STOPPED,
                        service!!.someipStatus!!.state,
                    )
                    assertFalse(VsomeipClient.isAvailable)
                    assertFalse(VsomeipClient.buildAndSend(1, 1, 0, 0, 0))
                    assertFalse(
                        "duplicate stop must not find a running service",
                        context!!.stopService(serviceIntent),
                    )
                    val logs = logcat()
                    assertEquals(1, occurrences(logs, "Service onCreate id=" + id).toLong())
                    assertEquals(1, occurrences(logs, "Service onDestroy id=" + id).toLong())
                    assertEquals(1, occurrences(logs, "SOMEIP_RELEASED id=" + id).toLong())
                    assertEquals(
                        (releaseBaseline + cycle + 1).toLong(),
                        occurrences(logs, "SOMEIP_RELEASED id=").toLong(),
                    )
                    assertEquals(
                        (nativeStopBaseline + cycle + 1).toLong(),
                        occurrences(logs, "vsomeip app stopped").toLong(),
                    )
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=stopped passed id=" + id)
                } finally {
                    instrumentation!!.runOnMainSync(Runnable { activity!!.finish() })
                    await(
                        "Activity finishes after the cycle",
                        Condition { activity!!.isDestroyed() },
                    )
                }
            }
        } finally {
            context!!.stopService(serviceIntent)
            instrumentation!!.waitForIdleSync()
            if (original == null) Files.deleteIfExists(config.toPath())
            else Files.write(config.toPath(), original)
        }
    }

    private fun onActivity(
        activity: MainActivity?,
        action: java.util.function.Consumer<MainActivity?>?,
    ) {
        instrumentation!!.runOnMainSync { action!!.accept(activity) }
    }

    @Throws(Exception::class)
    private fun boundService(activity: MainActivity?): VehicleSendService? {
        val field = MainActivity::class.java.getDeclaredField("vehicleService")
        field!!.setAccessible(true)
        val result = AtomicReference<VehicleSendService?>()
        instrumentation!!.runOnMainSync {
            try {
                result.set(field!!.get(activity) as VehicleSendService?)
            } catch (error: IllegalAccessException) {
                throw AssertionError(error)
            }
        }
        return result.get()
    }

    private fun interface Condition {
        @Throws(Exception::class) fun get(): Boolean
    }

    @Throws(Exception::class)
    private fun await(message: String?, condition: Condition?) {
        val deadline = SystemClock.elapsedRealtime() + 12000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition!!.get()) return
            // Bounded polling is confined to this opt-in real-network acceptance probe.
            SystemClock.sleep(100)
        }
        fail(message!! + " (12 s timeout)")
    }

    @Throws(Exception::class)
    private fun logcat(): String? =
        shell("logcat -d -v brief -s VEHICLE_SERVICE:I VSOMEIP_JNI:I SERVICE_PROBE:I '*:S'")

    @Throws(Exception::class)
    private fun shell(command: String?): String? {
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation!!.getUiAutomation().executeShellCommand(command)
            )
            .use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(4096)
                    var count = input.read(buffer)
                    while (count != -1) {
                        output.write(buffer, 0, count)
                        count = input.read(buffer)
                    }
                    return output.toString(StandardCharsets.UTF_8!!.name())
                }
            }
    }

    private fun occurrences(text: String?, needle: String?): Int {
        var count = 0
        var index = text!!.indexOf(needle!!, 0)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }
}
