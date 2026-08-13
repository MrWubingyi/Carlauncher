package com.example.carlauncher.model;

public enum Gear {
    P(0),
    R(1),
    N(2),
    D(3);

    private final int value;
    Gear(int value) { this.value = value; }
    public int getValue() { return value; }

    public static Gear fromString(String gearStr) {
        if (gearStr == null) return P;
        switch (gearStr.toUpperCase()) {
            case "R": return R;
            case "N": return N;
            case "D": return D;
            case "P":
            default: return P;
        }
    }
}
