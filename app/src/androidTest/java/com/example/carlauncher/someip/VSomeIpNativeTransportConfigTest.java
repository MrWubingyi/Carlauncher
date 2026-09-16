package com.example.carlauncher.someip;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VSomeIpNativeTransportConfigTest {
    @Test public void localAddressRewritePreservesRemoteEndpointAndDiscoveryMode() throws Exception {
        File file = File.createTempFile("someip-config-", ".json",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try {
            Files.write(file.toPath(), ("{\"unicast\":\"192.0.2.10\",\"services\":[{"
                    + "\"unicast\":\"192.168.31.248\",\"unreliable\":\"30509\"}],"
                    + "\"service-discovery\":{\"enable\":\"false\"}}")
                    .getBytes(StandardCharsets.UTF_8));
            VSomeIpNativeTransport.rewriteUnicast(file, "10.0.2.16");
            byte[] first = Files.readAllBytes(file.toPath());
            JSONObject json = new JSONObject(new String(first, StandardCharsets.UTF_8));
            assertEquals("10.0.2.16", json.getString("unicast"));
            assertEquals("192.168.31.248", json.getJSONArray("services").getJSONObject(0).getString("unicast"));
            assertEquals("false", json.getJSONObject("service-discovery").getString("enable"));
            VSomeIpNativeTransport.rewriteUnicast(file, "10.0.2.16");
            assertArrayEquals("same address must not rewrite the file", first, Files.readAllBytes(file.toPath()));
            byte[] invalid = "{invalid".getBytes(StandardCharsets.UTF_8);
            Files.write(file.toPath(), invalid);
            assertThrows(org.json.JSONException.class, () -> VSomeIpNativeTransport.rewriteUnicast(file, "10.0.2.16"));
            assertArrayEquals("invalid config must fail before opening output", invalid, Files.readAllBytes(file.toPath()));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }
}
