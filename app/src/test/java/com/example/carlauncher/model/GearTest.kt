package com.example.carlauncher.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Gear 枚举的字符串解析与线值映射。 */
open class GearTest {

    @Test
    open fun fromString_mapsKnownValues() {
        assertEquals(Gear.P, Gear.fromString("P"))
        assertEquals(Gear.R, Gear.fromString("R"))
        assertEquals(Gear.N, Gear.fromString("N"))
        assertEquals(Gear.D, Gear.fromString("D"))
    }

    @Test
    open fun fromString_isCaseInsensitive() {
        assertEquals(Gear.P, Gear.fromString("p"))
        assertEquals(Gear.R, Gear.fromString("r"))
        assertEquals(Gear.N, Gear.fromString("n"))
        assertEquals(Gear.D, Gear.fromString("d"))
    }

    @Test
    open fun fromString_nullDefaultsToPark() {
        assertEquals(Gear.P, Gear.fromString(null))
    }

    @Test
    open fun fromString_unknownDefaultsToPark() {
        assertEquals(Gear.P, Gear.fromString(""))
        assertEquals(Gear.P, Gear.fromString("X"))
        assertEquals(Gear.P, Gear.fromString(" "))
        assertEquals(Gear.P, Gear.fromString("DRIVE"))
    }

    @Test
    open fun getValue_matchesWireContract() {
        assertEquals(0, Gear.P.value.toLong())
        assertEquals(1, Gear.R.value.toLong())
        assertEquals(2, Gear.N.value.toLong())
        assertEquals(3, Gear.D.value.toLong())
    }
}
