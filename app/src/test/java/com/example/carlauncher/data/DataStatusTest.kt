package com.example.carlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** DataStatus 枚举与协议线值的映射契约。 */
open class DataStatusTest {

    @Test
    open fun getValue_matchesWireContract() {
        assertEquals(0, DataStatus.NORMAL.value.toLong())
        assertEquals(1, DataStatus.INVALID.value.toLong())
        assertEquals(2, DataStatus.NO_DATA.value.toLong())
        assertEquals(3, DataStatus.SOURCE_DISCONNECTED.value.toLong())
        assertEquals(4, DataStatus.TRANSPORT_DISCONNECTED.value.toLong())
    }
}
