package com.zqautomotive.vehicle.someip.android.jni

import com.zqautomotive.vehicle.someip.codec.SomeipPayloadCodec
import com.zqautomotive.vehicle.someip.android.VehicleSomeIpClientFactory
import com.example.someip.SomeIpClient

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/* Opt-in private-LAN integration probe; ordinary test runs do not start networking. */
@RunWith(AndroidJUnit4::class)
open class VsomeipNativeProbeTest {
    @Test
    @Throws(Exception::class)
    open fun requestResponseRejectAndRestart() {
        val peer = InstrumentationRegistry.getArguments().getString("someipPeer")
        val local = InstrumentationRegistry.getArguments().getString("someipLocal")
        Assume.assumeTrue(
            "Requires explicit someipPeer and someipLocal",
            peer != null && local != null,
        )
        val config = probeConfig(peer, local)
        runMethodProbe(config)
    }

    @Test
    @Throws(Exception::class)
    open fun discoversServiceAndExchangesMethods() {
        val local = InstrumentationRegistry.getArguments().getString("someipLocal")
        Assume.assumeTrue(
            "Requires explicit someipSd=true and someipLocal",
            "true" == InstrumentationRegistry.getArguments().getString("someipSd") && local != null,
        )
        // No remote unicast or business port: availability must come from SOME/IP-SD.
        val json =
            JSONObject()
                .put("client-endpoint", endpointConfig())
                .put("unicast", local)
                .put("logging", JSONObject().put("level", "info").put("console", "true"))
                .put(
                    "applications",
                    JSONArray()
                        .put(JSONObject().put("name", "soc-vehicle-client").put("id", "0x5566")),
                )!!
                .put("routing", "soc-vehicle-client")
                .put(
                    "service-discovery",
                    JSONObject()
                        .put("enable", "true")
                        .put("multicast", "224.244.224.245")
                        .put("port", "30490")
                        .put("protocol", "udp")
                        .put("ttl", "3")
                        .put("initial_delay_min", "10")
                        .put("initial_delay_max", "100")
                        .put("repetitions_base_delay", "200")
                        .put("repetitions_max", "3"),
                )
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val config = File(context!!.getFilesDir(), "vsomeip-sd-probe.json")
        FileOutputStream(config).use { out ->
            out.write(json!!.toString(2).toByteArray(StandardCharsets.UTF_8))
        }
        runMethodProbe(config)
    }

    @Throws(Exception::class)
    private fun probeConfig(peer: String?, local: String?): File? {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val config = File(context!!.getFilesDir(), "vsomeip-probe.json")
        val json =
            JSONObject()
                .put("client-endpoint", endpointConfig())
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
                .put("service-discovery", JSONObject().put("enable", "false"))
        FileOutputStream(config).use { out ->
            out.write(json!!.toString(2).toByteArray(StandardCharsets.UTF_8))
        }
        return config
    }

    private fun endpointConfig(): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return JSONObject(context.assets.open("vsomeip-client.json").bufferedReader().use { it.readText() })
            .getJSONObject("client-endpoint")
    }

    @Throws(Exception::class)
    private fun runMethodProbe(config: File?) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = VehicleSomeIpClientFactory.create(context, requireNotNull(config))
        val availability = LinkedBlockingQueue<Boolean?>()
        val responses = LinkedBlockingQueue<Int?>()
        val listener =
            object : SomeIpClient.Listener {
                override fun onAvailabilityChanged(available: Boolean) {
                    if (available) availability.offer(true)
                }

                override fun onPayload(payload: ByteArray) {}

                override fun onResponse(ok: Boolean, returnCode: Int) {
                    responses.offer(if (ok) 0 else if (returnCode == 0) -1 else returnCode)
                }
            }
        try {
            for (cycle in 0..1) {
                assertTrue("client start", client.start(listener))
                assertTrue(
                    "idempotent client start",
                    client.start(listener),
                )
                assertEquals(
                    "service availability",
                    java.lang.Boolean.TRUE,
                    availability.poll(10, TimeUnit.SECONDS),
                )
                val seq = 0x01020304 + cycle * 10
                sendAndExpect(
                    client,
                    responses,
                    SomeipPayloadCodec.encode(seq, 0x0102030405060708L, 100, 3, 0),
                    0,
                )
                sendAndExpect(client, responses, SomeipPayloadCodec.encode(seq + 1, 1, 201, 3, 0), 1)
                val badSchema = SomeipPayloadCodec.encode(seq + 2, 1, 0, 0, 0)
                badSchema[0] = 2
                sendAndExpect(client, responses, badSchema, 1)
                sendAndExpect(client, responses, SomeipPayloadCodec.encode(seq + 3, 1, 0, 0, 0), 0)
                assertFalse(
                    "short payload must be rejected locally",
                    sendVehicleRequest(client, ByteArray(15)),
                )
                client.stop()
                client.stop()
                assertFalse(client.isAvailable)
                assertFalse(
                    "stopped sends must not claim submission",
                    client.send(SomeipPayloadCodec.encode(1, 1, 0, 0, 0)),
                )
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                availability.clear()
                responses.clear()
                Log.i("VSOMEIP_PROBE", "cycle=" + cycle + " passed")
            }
        } finally {
            client.close()
        }
    }

    @Throws(Exception::class)
    private fun sendAndExpect(
        client: SomeIpClient,
        responses: LinkedBlockingQueue<Int?>?,
        payload: ByteArray?,
        expected: Int,
    ) {
        assertTrue("request submitted", sendVehicleRequest(client, payload))
        assertEquals(
            "response return code",
            Integer.valueOf(expected),
            responses!!.poll(5, TimeUnit.SECONDS),
        )
    }

    // The 16-byte requirement belongs to the legacy vehicle Method protocol, not the library.
    private fun sendVehicleRequest(client: SomeIpClient, payload: ByteArray?): Boolean =
        payload != null && payload.size == SomeipPayloadCodec.SIZE && client.send(payload)

    @Test
    @Throws(Exception::class)
    open fun nativeRegistrationAndRepeatedStopAreSafe() {
        val local = InstrumentationRegistry.getArguments().getString("someipLocal")
        Assume.assumeTrue(
            "Requires explicit someipLifecycle and local address",
            "true" == InstrumentationRegistry.getArguments().getString("someipLifecycle") &&
                local != null,
        )
        val config = probeConfig("192.0.2.1", local)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = VehicleSomeIpClientFactory.create(context, requireNotNull(config))
        val registered = LinkedBlockingQueue<Boolean?>()
        val listener =
            object : SomeIpClient.Listener {
                override fun onRegistered() {
                    registered.offer(true)
                }

                override fun onAvailabilityChanged(available: Boolean) {}

                override fun onPayload(payload: ByteArray) {}

                override fun onResponse(ok: Boolean, returnCode: Int) {}
            }
        try {
            for (cycle in 0..2) {
                assertTrue(client.start(listener))
                assertEquals(
                    "native routing registration",
                    java.lang.Boolean.TRUE,
                    registered.poll(5, TimeUnit.SECONDS),
                )
                assertTrue(
                    "duplicate start is idempotent",
                    client.start(listener),
                )
                client.stop()
                client.stop()
                assertFalse(client.isAvailable)
                assertFalse(sendVehicleRequest(client, null))
                assertFalse(sendVehicleRequest(client, ByteArray(15)))
                assertFalse(client.send(SomeipPayloadCodec.encode(1, 1, 0, 0, 0)))
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                registered.clear()
                Log.i("VSOMEIP_PROBE", "registration-stop cycle=" + cycle + " passed")
            }
        } finally {
            client.close()
        }
    }
}
