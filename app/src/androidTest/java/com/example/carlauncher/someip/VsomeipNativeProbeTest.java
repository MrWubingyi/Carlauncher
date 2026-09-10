package com.example.carlauncher.someip;

import android.content.Context;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Opt-in private-LAN integration probe; ordinary test runs do not start networking. */
@RunWith(AndroidJUnit4.class)
public class VsomeipNativeProbeTest {
    @Test public void requestResponseRejectAndRestart() throws Exception {
        String peer = InstrumentationRegistry.getArguments().getString("someipPeer");
        String local = InstrumentationRegistry.getArguments().getString("someipLocal");
        Assume.assumeTrue("Requires explicit someipPeer and someipLocal", peer != null && local != null);
        File config = probeConfig(peer, local);
        runMethodProbe(config);
    }

    @Test public void discoversServiceAndExchangesMethods() throws Exception {
        String local = InstrumentationRegistry.getArguments().getString("someipLocal");
        Assume.assumeTrue("Requires explicit someipSd=true and someipLocal",
                "true".equals(InstrumentationRegistry.getArguments().getString("someipSd")) && local != null);
        // No remote unicast or business port: availability must come from SOME/IP-SD.
        JSONObject json = new JSONObject()
                .put("unicast", local)
                .put("logging", new JSONObject().put("level", "info").put("console", "true"))
                .put("applications", new JSONArray().put(new JSONObject().put("name", "soc-vehicle-client").put("id", "0x5566")))
                .put("routing", "soc-vehicle-client")
                .put("service-discovery", new JSONObject().put("enable", "true")
                        .put("multicast", "224.244.224.245").put("port", "30490").put("protocol", "udp")
                        .put("ttl", "3").put("initial_delay_min", "10").put("initial_delay_max", "100")
                        .put("repetitions_base_delay", "200").put("repetitions_max", "3"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File config = new File(context.getFilesDir(), "vsomeip-sd-probe.json");
        try (FileOutputStream out = new FileOutputStream(config)) {
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        runMethodProbe(config);
    }

    private File probeConfig(String peer, String local) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File config = new File(context.getFilesDir(), "vsomeip-probe.json");
        JSONObject json = new JSONObject()
                .put("unicast", local)
                .put("logging", new JSONObject().put("level", "info").put("console", "true"))
                .put("applications", new JSONArray().put(new JSONObject().put("name", "soc-vehicle-client").put("id", "0x5566")))
                .put("routing", "soc-vehicle-client")
                .put("services", new JSONArray().put(new JSONObject().put("service", "0x1111")
                        .put("instance", "0x2222").put("unicast", peer).put("unreliable", "30509")))
                .put("service-discovery", new JSONObject().put("enable", "false"));
        try (FileOutputStream out = new FileOutputStream(config)) {
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return config;
    }

    private void runMethodProbe(File config) throws Exception {
        LinkedBlockingQueue<Boolean> availability = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Integer> responses = new LinkedBlockingQueue<>();
        VsomeipClient.Listener listener = new VsomeipClient.Listener() {
            @Override public void onAvailable(boolean available) { if (available) availability.offer(true); }
            @Override public void onResponse(boolean ok, int code) {
                responses.offer(ok ? 0 : code == 0 ? -1 : code);
            }
        };
        try {
            VsomeipClient.setListener(listener);
            for (int cycle = 0; cycle < 2; cycle++) {
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                assertTrue("nativeStart", VsomeipClient.start(context, config.getAbsolutePath()));
                assertTrue("idempotent nativeStart", VsomeipClient.start(context, config.getAbsolutePath()));
                assertEquals("service availability", Boolean.TRUE, availability.poll(10, TimeUnit.SECONDS));
                int seq = 0x01020304 + cycle * 10;
                sendAndExpect(responses, SomeipPayloadCodec.encode(seq, 0x0102030405060708L, 100, 3, 0), 0);
                sendAndExpect(responses, SomeipPayloadCodec.encode(seq + 1, 1, 201, 3, 0), 1);
                byte[] badSchema = SomeipPayloadCodec.encode(seq + 2, 1, 0, 0, 0);
                badSchema[0] = 2;
                sendAndExpect(responses, badSchema, 1);
                sendAndExpect(responses, SomeipPayloadCodec.encode(seq + 3, 1, 0, 0, 0), 0);
                assertFalse("short payload must be rejected locally", VsomeipClient.nativeSendState(new byte[15]));
                VsomeipClient.stop();
                VsomeipClient.stop();
                assertFalse(VsomeipClient.isAvailable());
                assertFalse("stopped sends must not claim submission", VsomeipClient.buildAndSend(1, 1, 0, 0, 0));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                availability.clear();
                responses.clear();
                Log.i("VSOMEIP_PROBE", "cycle=" + cycle + " passed");
            }
        } finally {
            VsomeipClient.clearListener(listener);
            VsomeipClient.stop();
        }
    }

    private void sendAndExpect(LinkedBlockingQueue<Integer> responses, byte[] payload, int expected) throws Exception {
        assertTrue("request submitted", VsomeipClient.nativeSendState(payload));
        assertEquals("response return code", Integer.valueOf(expected), responses.poll(5, TimeUnit.SECONDS));
    }

    @Test public void nativeRegistrationAndRepeatedStopAreSafe() throws Exception {
        String local = InstrumentationRegistry.getArguments().getString("someipLocal");
        Assume.assumeTrue("Requires explicit someipLifecycle and local address",
                "true".equals(InstrumentationRegistry.getArguments().getString("someipLifecycle")) && local != null);
        File config = probeConfig("192.0.2.1", local);
        LinkedBlockingQueue<Boolean> registered = new LinkedBlockingQueue<>();
        VsomeipClient.Listener listener = new VsomeipClient.Listener() {
            @Override public void onRegistered() { registered.offer(true); }
            @Override public void onAvailable(boolean available) {}
            @Override public void onResponse(boolean ok, int code) {}
        };
        try {
            VsomeipClient.setListener(listener);
            for (int cycle = 0; cycle < 3; cycle++) {
                assertTrue(VsomeipClient.start(config.getAbsolutePath()));
                assertEquals("native routing registration", Boolean.TRUE, registered.poll(5, TimeUnit.SECONDS));
                assertTrue("duplicate start is idempotent", VsomeipClient.start(config.getAbsolutePath()));
                VsomeipClient.stop();
                VsomeipClient.stop();
                assertFalse(VsomeipClient.isAvailable());
                assertFalse(VsomeipClient.nativeSendState(null));
                assertFalse(VsomeipClient.nativeSendState(new byte[15]));
                assertFalse(VsomeipClient.buildAndSend(1, 1, 0, 0, 0));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                registered.clear();
                Log.i("VSOMEIP_PROBE", "registration-stop cycle=" + cycle + " passed");
            }
        } finally {
            VsomeipClient.clearListener(listener);
            VsomeipClient.stop();
        }
    }
}
