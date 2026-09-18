package com.example.carlauncher.someip;

import com.example.carlauncher.model.VehicleState;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class VehicleEventLengthContractTest {
    /** VS-10: exact inclusive byte limit, with otherwise valid JSON. */
    @Test public void decode_acceptsExactly4096Bytes() throws Exception {
        byte[] original = ApprovedEventFixture.bytes(ApprovedEventFixture.frame());
        byte[] padded = Arrays.copyOf(original, 4096);
        Arrays.fill(padded, original.length, padded.length, (byte) ' ');
        VehicleState state = VehicleEventCodec.decode(padded);
        assertEquals(101L, state.getSequence());
        assertEquals(1700000000100L, state.getTimestampMs());
        assertEquals(36, state.getVehSpeedKph());
    }
}
