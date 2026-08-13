package com.example.carlauncher.model;

public enum WarningState {
    NONE(0),
    GENERAL_WARNING(1),
    CRITICAL(2);

    private final int value;
    WarningState(int value) { this.value = value; }
    public int getValue() { return value; }
}
