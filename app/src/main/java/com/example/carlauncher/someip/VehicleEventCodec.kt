package com.example.carlauncher.someip

import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.model.*
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

/** Event 0x8001: complete UTF-8 JSON vehicle snapshot, version 1. */
class VehicleEventCodec private constructor() {
    companion object {

        @Throws(JSONException::class)
        @JvmStatic
        private fun integer(json: JSONObject, name: String): Long {
            val value = json!!.get(name)
            if (!(value is Number)) throw JSONException("Not an integer: " + name)
            val number = value as Number?
            if (
                !java.lang.Double.isFinite(number!!.toDouble()) ||
                    number!!.toDouble() != number!!.toLong().toDouble()
            )
                throw JSONException("Not an integer: " + name)
            return number!!.toLong()
        }

        @Throws(JSONException::class)
        @JvmStatic
        private fun bounded(json: JSONObject, name: String, min: Int, max: Int): Int {
            val value = integer(json, name)
            if (value < min || value > max) throw JSONException("Out of range: " + name)
            return value.toInt()
        }

        @Throws(JSONException::class)
        @JvmStatic
        private fun number(json: JSONObject, name: String): Float {
            val value = json!!.get(name)
            if (!(value is Number) || !java.lang.Float.isFinite((value as Number).toFloat()))
                throw JSONException("Not a finite number: " + name)
            return (value as Number).toFloat()
        }

        @Throws(JSONException::class)
        @JvmStatic
        private fun flag(json: JSONObject, name: String): Boolean {
            val value = json!!.get(name)
            if (!(value is Boolean)) throw JSONException("Not boolean: " + name)
            return (value as Boolean?)!!
        }

        @Throws(JSONException::class)
        @JvmStatic
        fun decode(payload: ByteArray?): VehicleState {
            if (payload == null || payload!!.size == 0 || payload!!.size > 4096)
                throw JSONException("Invalid event length")
            // Some JSON parsers treat a raw NUL as end-of-input and ignore the suffix.
            for (value in payload!!) {
                if (value.toInt() == 0) throw JSONException("NUL in event payload")
            }
            val j = JSONObject(String(payload, StandardCharsets.UTF_8))
            bounded(j, "version", 1, 1)
            val seq = integer(j, "seq")
            val timestamp = integer(j, "timestampMs")
            if (seq < 0 || timestamp <= 0) throw JSONException("Invalid event identity")
            val speed = bounded(j, "speedKph", Integer.MIN_VALUE, Integer.MAX_VALUE)
            val rpm = bounded(j, "rpm", Integer.MIN_VALUE, Integer.MAX_VALUE)
            val soc = bounded(j, "soc", Integer.MIN_VALUE, Integer.MAX_VALUE)
            val temp = number(j, "engineCoolantTemp")
            val battery = number(j, "evBatteryLevel")
            var validity = bounded(j, "validity", 0, 3)
            var status = bounded(j, "dataStatus", 0, 4)
            val invalid =
                (speed < 0 ||
                    speed > 200 ||
                    rpm < 0 ||
                    rpm > 8000 ||
                    soc < 0 ||
                    soc > 100 ||
                    temp < -50 ||
                    temp > 200 ||
                    battery < 0 ||
                    battery > 100)
            if (speed < 0 || speed > 200) validity = 1 else if (invalid || status == 1) validity = 2
            if (invalid) status = 1
            return VehicleState.Builder()
                .setVersion(1)
                .setSequence(seq)
                .setTimestampMs(timestamp)
                .setVehSpeedKph(speed)
                .setEngRpm(rpm)
                .setSoc(soc)
                .setGear(Gear.values()[bounded(j, "gear", 0, 3)])
                .setTurnSignal(TurnSignal.values()[bounded(j, "turnSignal", 0, 3)])
                .setWarning(WarningState.values()[bounded(j, "warning", 0, 2)])
                .setValidity(DataValidity.values()[validity])
                .setDataStatus(DataStatus.values()[status])
                .setParkingBrake(flag(j, "parkingBrake"))
                .setDoorLock(flag(j, "doorLock"))
                .setBeltWarning(flag(j, "beltWarning"))
                .setHeadlightsState(bounded(j, "headlightsState", 0, 100))
                .setHighBeamLightsState(bounded(j, "highBeamLightsState", 0, 100))
                .setEngineCoolantTemp(temp)
                .setEvBatteryLevel(battery)
                .build()
        }
    }
}
