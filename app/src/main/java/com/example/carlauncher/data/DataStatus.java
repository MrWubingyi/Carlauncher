package com.example.carlauncher.data;

public enum DataStatus {
    NORMAL(0),
    INVALID(1),
    NO_DATA(2),
    SOURCE_DISCONNECTED(3),
    TRANSPORT_DISCONNECTED(4);

    private final int value;
    DataStatus(int value) { this.value = value; }
    public int getValue() { return value; }
}
