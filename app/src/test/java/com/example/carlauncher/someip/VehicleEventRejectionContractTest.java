package com.example.carlauncher.someip;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import static org.junit.Assert.*;

/** VS-09/10/27: invalid wire data must be rejected, not coerced. */
@RunWith(Parameterized.class)
public class VehicleEventRejectionContractTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() throws Exception {
        Collection<Object[]> rows = new ArrayList<>();
        JSONObject baseline = ApprovedEventFixture.frame();
        java.util.Iterator<String> fields = baseline.keys();
        while (fields.hasNext()) {
            String field = fields.next();
            JSONObject missing = ApprovedEventFixture.frame();
            missing.remove(field);
            rows.add(new Object[]{"missing " + field, ApprovedEventFixture.bytes(missing)});
            rows.add(new Object[]{"null " + field, changed(field, JSONObject.NULL)});
            rows.add(new Object[]{"string " + field, changed(field, "wrong-type")});
        }
        for (String field : new String[]{"parkingBrake", "doorLock", "beltWarning"}) {
            rows.add(new Object[]{"numeric boolean " + field, changed(field, 1)});
        }
        Object[][] invalid = {{"version", 2}, {"seq", -1}, {"seq", 1.5},
                {"timestampMs", 0}, {"timestampMs", -1}, {"timestampMs", 1.5},
                {"speedKph", 1.5}, {"rpm", 1.5}, {"soc", 1.5},
                {"gear", -1}, {"gear", 4}, {"turnSignal", -1}, {"turnSignal", 4},
                {"warning", -1}, {"warning", 3}, {"validity", -1}, {"validity", 4},
                {"dataStatus", -1}, {"dataStatus", 5}, {"rpm", 2147483648L}};
        for (Object[] pair : invalid) {
            rows.add(new Object[]{pair[0] + "=" + pair[1], changed((String) pair[0], pair[1])});
        }
        rows.add(new Object[]{"null payload", null});
        rows.add(new Object[]{"empty payload", new byte[0]});
        rows.add(new Object[]{"array root", "[]".getBytes(StandardCharsets.UTF_8)});
        rows.add(new Object[]{"truncated object", "{\"version\":1".getBytes(StandardCharsets.UTF_8)});
        byte[] valid = ApprovedEventFixture.bytes(baseline);
        byte[] oversized = Arrays.copyOf(valid, 4097);
        Arrays.fill(oversized, valid.length, oversized.length, (byte) ' ');
        rows.add(new Object[]{"4097 bytes", oversized});
        // NUL followed by content must not be treated as a valid JSON terminator.
        rows.add(new Object[]{"embedded NUL", (baseline.toString() + "\0garbage")
                .getBytes(StandardCharsets.UTF_8)});
        return rows;
    }

    private static byte[] changed(String field, Object value) throws Exception {
        return ApprovedEventFixture.bytes(ApprovedEventFixture.frame().put(field, value));
    }

    private final byte[] payload;

    public VehicleEventRejectionContractTest(String name, byte[] payload) {
        this.payload = payload;
    }

    @Test public void decode_rejectsInvalidWireData() {
        assertThrows(JSONException.class, () -> VehicleEventCodec.decode(payload));
    }
}
