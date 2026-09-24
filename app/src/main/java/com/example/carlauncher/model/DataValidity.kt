package com.example.carlauncher.model

enum class DataValidity private constructor(val value: Int) {
    VALID(0),
    INVALID_SPEED(1),
    INCOMPLETE(2),
    STALE(3),
}
