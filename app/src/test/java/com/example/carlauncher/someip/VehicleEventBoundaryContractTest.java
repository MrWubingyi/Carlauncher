package com.example.carlauncher.someip;

import com.example.carlauncher.data.DataStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.ArrayList;
import java.util.Collection;
import static org.junit.Assert.*;

/** VS-08: approved Q1 ranges; decoding only, not watchdog acceptance. */
@RunWith(Parameterized.class)
public class VehicleEventBoundaryContractTest {
    @Parameterized.Parameters(name = "{0}={1}, accepted={2}")
    public static Collection<Object[]> cases() {
        Collection<Object[]> rows = new ArrayList<>();
        boundary(rows, "speedKph", 0, 200);
        boundary(rows, "rpm", 0, 8000);
        boundary(rows, "soc", 0, 100);
        boundary(rows, "engineCoolantTemp", -50, 200);
        boundary(rows, "evBatteryLevel", 0, 100);
        boundary(rows, "headlightsState", 0, 100);
        boundary(rows, "highBeamLightsState", 0, 100);
        return rows;
    }

    private static void boundary(Collection<Object[]> rows, String field, int min, int max) {
        rows.add(new Object[]{field, min - 1, false});
        rows.add(new Object[]{field, min, true});
        rows.add(new Object[]{field, max, true});
        rows.add(new Object[]{field, max + 1, false});
    }

    private final String field;
    private final int value;
    private final boolean accepted;

    public VehicleEventBoundaryContractTest(String field, int value, boolean accepted) {
        this.field = field;
        this.value = value;
        this.accepted = accepted;
    }

    @Test public void decode_enforcesApprovedBoundary() throws Exception {
        byte[] payload = ApprovedEventFixture.bytes(ApprovedEventFixture.frame().put(field, value));
        if (!accepted && (field.equals("headlightsState") || field.equals("highBeamLightsState"))) {
            assertThrows(JSONException.class, () -> VehicleEventCodec.decode(payload));
            return;
        }
        VehicleState state = VehicleEventCodec.decode(payload);
        assertEquals(accepted ? DataStatus.NORMAL : DataStatus.INVALID, state.getDataStatus());
        assertEquals(accepted ? DataValidity.VALID
                : field.equals("speedKph") ? DataValidity.INVALID_SPEED : DataValidity.INCOMPLETE,
                state.getValidity());
    }
}
