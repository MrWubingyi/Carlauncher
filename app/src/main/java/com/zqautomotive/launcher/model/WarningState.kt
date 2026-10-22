package com.zqautomotive.launcher.model

enum class WarningState private constructor(val value: Int) {
    NONE(0),
    GENERAL_WARNING(1),
    CRITICAL(2),
}
