package com.example.carlauncher.someip

import org.junit.Assert.*
import org.junit.Test

open class SomeipPayloadCodecTest {
    @Test
    open fun frozenVectorUsesBigEndianAtEveryOffset() {
        val expected = byteArrayOf(1, 1, 2, 3, 4, 1, 2, 3, 4, 5, 6, 7, 8, 100, 3, 0)
        assertArrayEquals(
            expected,
            SomeipPayloadCodec.encode(0x01020304, 0x0102030405060708L, 100, 3, 0),
        )
        assertArrayEquals(
            longArrayOf(1, 0x01020304L, 0x0102030405060708L, 100, 3, 0),
            SomeipPayloadCodec.decode(expected),
        )
    }

    @Test
    open fun unsignedSequenceAndInclusiveBoundsArePreserved() {
        assertArrayEquals(
            longArrayOf(1, 0xffffffffL, 1, 200, 3, 4),
            SomeipPayloadCodec.decode(SomeipPayloadCodec.encode(-1, 1, 200, 3, 4)),
        )
        assertArrayEquals(
            longArrayOf(1, 0, 1, 0, 0, 0),
            SomeipPayloadCodec.decode(SomeipPayloadCodec.encode(0, 1, 0, 0, 0)),
        )
    }

    @Test
    open fun invalidInputsUseSentinelsInsteadOfWrapping() {
        for (values in
            arrayOf<IntArray?>(
                intArrayOf(-1, -1, -1),
                intArrayOf(201, 4, 5),
                intArrayOf(256, 256, 256),
            )) {
            val decoded =
                SomeipPayloadCodec.decode(
                    SomeipPayloadCodec.encode(1, 1, values!![0], values!![1], values!![2])
                )
            assertArrayEquals(longArrayOf(1, 1, 1, 255, 255, 255), decoded)
        }
    }

    @Test
    open fun malformedLengthsAreRejected() {
        for (input in arrayOf<ByteArray?>(null, ByteArray(0), ByteArray(15), ByteArray(17))) {
            assertThrows<IllegalArgumentException?>(IllegalArgumentException::class.java) {
                SomeipPayloadCodec.decode(input)
            }
        }
    }
}
