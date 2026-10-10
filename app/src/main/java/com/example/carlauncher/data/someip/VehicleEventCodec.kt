package com.example.carlauncher.data.someip

import com.example.carlauncher.model.VehicleState
import com.example.vehicle.someip.codec.SomeIpEventCodec
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