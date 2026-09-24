package com.example.carlauncher.ui

enum class CockpitConnectionState
private constructor(
    val actionLabel: String?,
    val isActionEnabled: Boolean,
    val isStopAction: Boolean,
) {
    ONLINE("STOP RECEIVE", true, true),
    CONNECTING("PLEASE WAIT", false, false),
    INVALID_DATA("STOP RECEIVE", true, true),
    DISCONNECTED("START RECEIVE", true, false),
    RECOVERING("PLEASE WAIT", false, false),
}
