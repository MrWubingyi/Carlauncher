package com.example.carlauncher.model;

public enum TurnSignal {
    NONE(0),
    LEFT(1),
    RIGHT(2),
    HAZARD(3);

    private final int value;
    TurnSignal(int value) { this.value = value; }
    public int getValue() { return value; }
}
