package com.example.carlauncher.someip;

import com.example.carlauncher.data.DataStatus;
import com.example.carlauncher.model.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Event 0x8001: complete UTF-8 JSON vehicle snapshot, version 1.
 */
public final class VehicleEventCodec {
    private VehicleEventCodec() {
    }

    private static long integer(JSONObject json, String name) throws JSONException {
        Object value = json.get(name);
        if (!(value instanceof Number)) throw new JSONException("Not an integer: " + name);
        Number number = (Number) value;
        if (!Double.isFinite(number.doubleValue()) || number.doubleValue() != number.longValue())
            throw new JSONException("Not an integer: " + name);
        return number.longValue();
    }

    private static int bounded(JSONObject json, String name, int min, int max) throws JSONException {
        long value = integer(json, name);
        if (value < min || value > max) throw new JSONException("Out of range: " + name);
        return (int) value;
    }

    private static float number(JSONObject json, String name) throws JSONException {
        Object value = json.get(name);
        if (!(value instanceof Number) || !Float.isFinite(((Number) value).floatValue()))
            throw new JSONException("Not a finite number: " + name);
        return ((Number) value).floatValue();
    }

    private static boolean flag(JSONObject json, String name) throws JSONException {
        Object value = json.get(name);
        if (!(value instanceof Boolean)) throw new JSONException("Not boolean: " + name);
        return (Boolean) value;
    }

    public static VehicleState decode(byte[] payload) throws JSONException {
        if (payload == null || payload.length == 0 || payload.length > 4096)
            throw new JSONException("Invalid event length");
        // Some JSON parsers treat a raw NUL as end-of-input and ignore the suffix.
        for (byte value : payload) {
            if (value == 0) throw new JSONException("NUL in event payload");
        }
        JSONObject j = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        bounded(j, "version", 1, 1);
        long seq = integer(j, "seq"), timestamp = integer(j, "timestampMs");
        if (seq < 0 || timestamp <= 0) throw new JSONException("Invalid event identity");
        int speed = bounded(j, "speedKph", Integer.MIN_VALUE, Integer.MAX_VALUE);
        int rpm = bounded(j, "rpm", Integer.MIN_VALUE, Integer.MAX_VALUE);
        int soc = bounded(j, "soc", Integer.MIN_VALUE, Integer.MAX_VALUE);
        float temp = number(j, "engineCoolantTemp"), battery = number(j, "evBatteryLevel");
        int validity = bounded(j, "validity", 0, 3);
        int status = bounded(j, "dataStatus", 0, 4);
        boolean invalid = speed < 0 || speed > 200 || rpm < 0 || rpm > 8000
                || soc < 0 || soc > 100 || temp < -50 || temp > 200 || battery < 0 || battery > 100;
        if (speed < 0 || speed > 200) validity = 1;
        else if (invalid || status == 1) validity = 2;
        if (invalid) status = 1;
        return new VehicleState.Builder().setVersion(1).setSequence(seq).setTimestampMs(timestamp)
                .setVehSpeedKph(speed).setEngRpm(rpm).setSoc(soc)
                .setGear(Gear.values()[bounded(j, "gear", 0, 3)])
                .setTurnSignal(TurnSignal.values()[bounded(j, "turnSignal", 0, 3)])
                .setWarning(WarningState.values()[bounded(j, "warning", 0, 2)])
                .setValidity(DataValidity.values()[validity]).setDataStatus(DataStatus.values()[status])
                .setParkingBrake(flag(j, "parkingBrake")).setDoorLock(flag(j, "doorLock"))
                .setBeltWarning(flag(j, "beltWarning"))
                .setHeadlightsState(bounded(j, "headlightsState", 0, 100))
                .setHighBeamLightsState(bounded(j, "highBeamLightsState", 0, 100))
                .setEngineCoolantTemp(temp).setEvBatteryLevel(battery).build();
    }
}
