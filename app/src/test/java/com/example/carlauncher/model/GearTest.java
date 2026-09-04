package com.example.carlauncher.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Gear 枚举的字符串解析与线值映射。
 */
public class GearTest {

    @Test
    public void fromString_mapsKnownValues() {
        assertEquals(Gear.P, Gear.fromString("P"));
        assertEquals(Gear.R, Gear.fromString("R"));
        assertEquals(Gear.N, Gear.fromString("N"));
        assertEquals(Gear.D, Gear.fromString("D"));
    }

    @Test
    public void fromString_isCaseInsensitive() {
        assertEquals(Gear.P, Gear.fromString("p"));
        assertEquals(Gear.R, Gear.fromString("r"));
        assertEquals(Gear.N, Gear.fromString("n"));
        assertEquals(Gear.D, Gear.fromString("d"));
    }

    @Test
    public void fromString_nullDefaultsToPark() {
        assertEquals(Gear.P, Gear.fromString(null));
    }

    @Test
    public void fromString_unknownDefaultsToPark() {
        assertEquals(Gear.P, Gear.fromString(""));
        assertEquals(Gear.P, Gear.fromString("X"));
        assertEquals(Gear.P, Gear.fromString(" "));
        assertEquals(Gear.P, Gear.fromString("DRIVE"));
    }

    @Test
    public void getValue_matchesWireContract() {
        assertEquals(0, Gear.P.getValue());
        assertEquals(1, Gear.R.getValue());
        assertEquals(2, Gear.N.getValue());
        assertEquals(3, Gear.D.getValue());
    }
}
