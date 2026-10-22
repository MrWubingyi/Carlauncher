package com.zqautomotive.launcher.data.someip

import com.zqautomotive.launcher.model.VehicleState
import com.zqautomotive.vehicle.someip.codec.SomeIpEventCodec
import org.json.JSONException

/** Compatibility facade. The live path decodes in the adapter and maps in the data source. */
class VehicleEventCodec private constructor() {
    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun decode(payload: ByteArray?): VehicleState =
            VehicleSignalMapper.toVehicleState(SomeIpEventCodec.decode(payload))
    }
}
