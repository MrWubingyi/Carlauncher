package com.example.carlauncher.someip;

import android.app.Instrumentation;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.carlauncher.MainActivity;
import com.example.carlauncher.R;
import com.example.carlauncher.service.VehicleSendService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Explicit LAN acceptance probe. Exercises the real Activity, Service, JNI and peer. */
@RunWith(AndroidJUnit4.class)
public class VehicleServiceLifecycleProbeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    @Test public void backgroundRebindAndRestartReleaseEachNativeClientOnce() throws Exception {
        String peer = InstrumentationRegistry.getArguments().getString("someipPeer");
        String local = InstrumentationRegistry.getArguments().getString("someipLocal");
        Assume.assumeTrue("Explicit someipServiceLifecycle=true and LAN addresses required",
                "true".equals(InstrumentationRegistry.getArguments().getString("someipServiceLifecycle"))
                        && peer != null && local != null);
        File config = new File(context.getFilesDir(), "vsomeip/vsomeip-client.json");
        byte[] original = config.exists() ? Files.readAllBytes(config.toPath()) : null;
        Intent serviceIntent = new Intent(context, VehicleSendService.class);
        // Refuse to disturb a service the user already started.
        for (ActivityManager.RunningServiceInfo info : context.getSystemService(ActivityManager.class).getRunningServices(100)) {
            assertNotEquals("probe requires a stopped VehicleSendService",
                    VehicleSendService.class.getName(), info.service.getClassName());
        }
        assertFalse("probe requires an idle native client", VsomeipClient.isAvailable());
        // These normal runtime prompts otherwise cover the Activity and may outlive
        // its task on AAOS. The opt-in probe runs in Gradle's disposable app install.
        for (String permission : new String[]{"android.permission.POST_NOTIFICATIONS", "android.car.permission.CAR_SPEED"}) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
            }
        }
        Files.createDirectories(config.toPath().getParent());
        JSONObject json = new JSONObject().put("unicast", local)
                .put("logging", new JSONObject().put("level", "info").put("console", "true"))
                .put("applications", new JSONArray().put(new JSONObject()
                        .put("name", "soc-vehicle-client").put("id", "0x5566")))
                .put("routing", "soc-vehicle-client")
                .put("services", new JSONArray().put(new JSONObject().put("service", "0x1111")
                        .put("instance", "0x2222").put("unicast", peer).put("unreliable", "30509")))
                .put("service-discovery", new JSONObject().put("enable", "false"));
        Files.write(config.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
        int releaseBaseline = occurrences(logcat(), "SOMEIP_RELEASED id=");
        int nativeStopBaseline = occurrences(logcat(), "vsomeip app stopped");
        VehicleSendService previous = null;
        try {
            for (int cycle = 0; cycle < 2; cycle++) {
                MainActivity activity = (MainActivity) instrumentation.startActivitySync(
                        new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                try {
                    onActivity(activity, value -> value.findViewById(R.id.connectButton).performClick());
                    await("Activity binds to a real online Service", () -> {
                        VehicleSendService service = boundService(activity);
                        return service != null && service.getSomeipStatus().getState() == SomeipConnectionMonitor.State.ONLINE;
                    });
                    VehicleSendService service = boundService(activity);
                    assertNotSame("restart must create a new Service", previous, service);
                    previous = service;
                    int id = System.identityHashCode(service);
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=foreground id=" + id);
                    onActivity(activity, value -> assertEquals("STOP SEND",
                            ((TextView) value.findViewById(R.id.connectButton)).getText().toString()));

                    shell("input keyevent KEYCODE_HOME");
                    await("Home must stop the Activity and release its binding", () -> {
                        AtomicReference<Boolean> background = new AtomicReference<>(false);
                        instrumentation.runOnMainSync(() -> background.set(
                                !activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)));
                        return background.get() && boundService(activity) == null;
                    });
                    long sequence = service.getLatestVehicleState().getSequence();
                    int responsesBefore = occurrences(logcat(), "SOMEIP_RX service=");
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=background id=" + id);
                    await("real Method replies continue after Activity unbind", () ->
                            service.getLatestVehicleState().getSequence() >= sequence + 5
                                    && occurrences(logcat(), "SOMEIP_RX service=") >= responsesBefore + 5);
                    assertEquals(SomeipConnectionMonitor.State.ONLINE, service.getSomeipStatus().getState());

                    // Bring the real task forward after Home; ActivityScenario's internal
                    // lifecycle transition alone does not foreground an AAOS task.
                    context.startActivity(new Intent(context, MainActivity.class).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    await("foreground rebind must retain Service identity", () -> boundService(activity) == service);
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=rebound id=" + id);
                    onActivity(activity, value -> {
                        assertEquals("STOP SEND", ((TextView) value.findViewById(R.id.connectButton)).getText().toString());
                        value.findViewById(R.id.connectButton).performClick();
                    });
                    await("native release completes", () -> logcat().contains("SOMEIP_RELEASED id=" + id));
                    assertNull(boundService(activity));
                    assertEquals(SomeipConnectionMonitor.State.STOPPED, service.getSomeipStatus().getState());
                    assertFalse(VsomeipClient.isAvailable());
                    assertFalse(VsomeipClient.buildAndSend(1, 1, 0, 0, 0));
                    assertFalse("duplicate stop must not find a running service", context.stopService(serviceIntent));
                    String logs = logcat();
                    assertEquals(1, occurrences(logs, "Service onCreate id=" + id));
                    assertEquals(1, occurrences(logs, "Service onDestroy id=" + id));
                    assertEquals(1, occurrences(logs, "SOMEIP_RELEASED id=" + id));
                    assertEquals(releaseBaseline + cycle + 1, occurrences(logs, "SOMEIP_RELEASED id="));
                    assertEquals(nativeStopBaseline + cycle + 1, occurrences(logs, "vsomeip app stopped"));
                    Log.i("SERVICE_PROBE", "cycle=" + cycle + " phase=stopped passed id=" + id);
                } finally {
                    instrumentation.runOnMainSync(activity::finish);
                    await("Activity finishes after the cycle", activity::isDestroyed);
                }
            }
        } finally {
            context.stopService(serviceIntent);
            instrumentation.waitForIdleSync();
            if (original == null) Files.deleteIfExists(config.toPath());
            else Files.write(config.toPath(), original);
        }
    }

    private void onActivity(MainActivity activity, java.util.function.Consumer<MainActivity> action) {
        instrumentation.runOnMainSync(() -> action.accept(activity));
    }
    private VehicleSendService boundService(MainActivity activity) throws Exception {
        Field field = MainActivity.class.getDeclaredField("vehicleService");
        field.setAccessible(true);
        AtomicReference<VehicleSendService> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { result.set((VehicleSendService) field.get(activity)); }
            catch (IllegalAccessException error) { throw new AssertionError(error); }
        });
        return result.get();
    }

    private interface Condition { boolean get() throws Exception; }
    private void await(String message, Condition condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.get()) return;
            // Bounded polling is confined to this opt-in real-network acceptance probe.
            SystemClock.sleep(100);
        }
        fail(message + " (12 s timeout)");
    }
    private String logcat() throws Exception {
        return shell("logcat -d -v brief -s VEHICLE_SERVICE:I VSOMEIP_JNI:I SERVICE_PROBE:I '*:S'");
    }
    private String shell(String command) throws Exception {
        try (FileInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                     instrumentation.getUiAutomation().executeShellCommand(command));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
