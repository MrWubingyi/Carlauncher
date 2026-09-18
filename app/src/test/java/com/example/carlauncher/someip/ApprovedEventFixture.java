package com.example.carlauncher.someip;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Approved review REV-b32dd43d4262406ab5266b25850c37fb, revision 1. */
final class ApprovedEventFixture {
    private ApprovedEventFixture() {}

    static JSONObject frame() throws Exception {
        return new JSONObject("{\"version\":1,\"seq\":101,\"timestampMs\":1700000000100,"
                + "\"speedKph\":36,\"rpm\":1700,\"gear\":3,\"soc\":70,\"turnSignal\":0,"
                + "\"parkingBrake\":false,\"warning\":0,\"validity\":0,\"doorLock\":true,"
                + "\"beltWarning\":false,\"headlightsState\":1,\"highBeamLightsState\":0,"
                + "\"engineCoolantTemp\":90,\"evBatteryLevel\":70,\"dataStatus\":0}");
    }

    static byte[] bytes(JSONObject frame) {
        return frame.toString().getBytes(StandardCharsets.UTF_8);
    }
}
