package com.example.carlauncher.someip;

import org.junit.Test;
import static org.junit.Assert.*;

public class SomeipPayloadCodecTest {
    @Test public void frozenVectorUsesBigEndianAtEveryOffset() {
        byte[] expected = {1, 1, 2, 3, 4, 1, 2, 3, 4, 5, 6, 7, 8, 100, 3, 0};
        assertArrayEquals(expected, SomeipPayloadCodec.encode(0x01020304, 0x0102030405060708L, 100, 3, 0));
        assertArrayEquals(new long[]{1, 0x01020304L, 0x0102030405060708L, 100, 3, 0},
                SomeipPayloadCodec.decode(expected));
    }

    @Test public void unsignedSequenceAndInclusiveBoundsArePreserved() {
        assertArrayEquals(new long[]{1, 0xffffffffL, 1, 200, 3, 4},
                SomeipPayloadCodec.decode(SomeipPayloadCodec.encode(-1, 1, 200, 3, 4)));
        assertArrayEquals(new long[]{1, 0, 1, 0, 0, 0},
                SomeipPayloadCodec.decode(SomeipPayloadCodec.encode(0, 1, 0, 0, 0)));
    }

    @Test public void invalidInputsUseSentinelsInsteadOfWrapping() {
        for (int[] values : new int[][]{{-1, -1, -1}, {201, 4, 5}, {256, 256, 256}}) {
            long[] decoded = SomeipPayloadCodec.decode(SomeipPayloadCodec.encode(1, 1, values[0], values[1], values[2]));
            assertArrayEquals(new long[]{1, 1, 1, 255, 255, 255}, decoded);
        }
    }

    @Test public void malformedLengthsAreRejected() {
        for (byte[] input : new byte[][]{null, new byte[0], new byte[15], new byte[17]}) {
            assertThrows(IllegalArgumentException.class, () -> SomeipPayloadCodec.decode(input));
        }
    }
}
