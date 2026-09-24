package com.example.carlauncher.data

enum class DataStatus private constructor(val value: Int) {
    NORMAL(0),
    INVALID(1),
    NO_DATA(2),
    SOURCE_DISCONNECTED(3),
    TRANSPORT_DISCONNECTED(4),
}
