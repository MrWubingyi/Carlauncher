package com.example.carlauncher.someip;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.carlauncher.MainActivity;
import com.example.carlauncher.R;
import com.example.carlauncher.service.VehicleSendService;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Opt-in real SD/Event -> foreground Service -> repository -> visible TextView test. */
@RunWith(AndroidJUnit4.class)
public class VehicleEventProbeTest {
    @Test public void remoteEventsUpdateActivity() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("someipEvents")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), "android.car.permission.CAR_SPEED");
        context.stopService(new Intent(context, VehicleSendService.class));
        // Use current production SD config, not a saved legacy static Method probe config.
        java.io.File config = new java.io.File(context.getFilesDir(), "vsomeip/vsomeip-client.json");
        byte[] previous = config.exists() ? java.nio.file.Files.readAllBytes(config.toPath()) : null;
        java.nio.file.Files.deleteIfExists(config.toPath());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.findViewById(R.id.connectButton).performClick());
            AtomicLong first = new AtomicLong(-1), latest = new AtomicLong(-1);
            long deadline = android.os.SystemClock.elapsedRealtime() + 45000;
            while (android.os.SystemClock.elapsedRealtime() < deadline && latest.get() < first.get() + 10) {
                scenario.onActivity(a -> {
                    String speed = ((TextView)a.findViewById(R.id.speedText)).getText().toString();
                    if (speed.startsWith("--")) return;
                    try {
                        for (java.lang.reflect.Field field : MainActivity.class.getDeclaredFields()) {
                            if (field.getType() != VehicleSendService.class) continue;
                            field.setAccessible(true);
                            VehicleSendService service = (VehicleSendService)field.get(a);
                            if (service == null || service.getLatestVehicleState() == null) return;
                            long seq = service.getLatestVehicleState().getSequence();
                            assertEquals(SomeipConnectionMonitor.State.ONLINE, service.getSomeipStatus().getState());
                            assertEquals(service.getLatestVehicleState().getVehSpeedKph() + " km/h", speed);
                            if (first.get() < 0) first.set(seq);
                            latest.set(seq);
                            android.util.Log.i("EVENT_UI_PROBE", "UI seq=" + seq + " speed=" + speed);
                        }
                    } catch (IllegalAccessException e) { throw new AssertionError(e); }
                });
                Thread.sleep(100);
            }
            assertTrue("At least ten changing remote snapshots must reach actual UI", first.get() >= 0 && latest.get() >= first.get() + 10);
            scenario.onActivity(a -> a.findViewById(R.id.connectButton).performClick());
        } finally {
            context.stopService(new Intent(context, VehicleSendService.class));
            if (previous != null) java.nio.file.Files.write(config.toPath(), previous);
        }
    }
}
