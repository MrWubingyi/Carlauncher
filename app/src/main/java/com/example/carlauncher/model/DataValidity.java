package com.example.carlauncher.model;

public enum DataValidity {
    VALID(0),
    INVALID_SPEED(1),
    INCOMPLETE(2),
    STALE(3);

    private final int value;
    DataValidity(int value) { this.value = value; }
    public int getValue() { return value; }
}
