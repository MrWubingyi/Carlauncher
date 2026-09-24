package com.example.carlauncher.model

enum class TurnSignal private constructor(val value: Int) {
    NONE(0),
    LEFT(1),
    RIGHT(2),
    HAZARD(3),
}
