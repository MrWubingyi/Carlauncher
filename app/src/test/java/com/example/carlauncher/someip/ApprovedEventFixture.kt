package com.example.carlauncher.someip

import java.nio.charset.StandardCharsets
import org.json.JSONObject

/* Approved review REV-b32dd43d4262406ab5266b25850c37fb, revision 1. */
internal class ApprovedEventFixture private constructor() {
    companion object {

        @Throws(Exception::class)
        @JvmStatic
        fun frame(): JSONObject? =
            JSONObject(
                ("{\"version\":1,\"seq\":101,\"timestampMs\":1700000000100," +
                    "\"speedKph\":36,\"rpm\":1700,\"gear\":3,\"soc\":70,\"turnSignal\":0," +
                    "\"parkingBrake\":false,\"warning\":0,\"validity\":0,\"doorLock\":true," +
                    "\"beltWarning\":false,\"headlightsState\":1,\"highBeamLightsState\":0," +
                    "\"engineCoolantTemp\":90,\"evBatteryLevel\":70,\"dataStatus\":0}")
            )

        @JvmStatic
        fun bytes(frame: JSONObject?): ByteArray =
            frame!!.toString().toByteArray(StandardCharsets.UTF_8)
    }
}
