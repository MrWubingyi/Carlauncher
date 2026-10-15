package com.example.vehicle.control

/** This allowlist deliberately excludes door actuation, driving, braking and general CAN access. */
enum class VehicleAction(val wireName: String, val label: String) {
    LOCK("lock", "锁车门"), UNLOCK("unlock", "解锁车门"), FIND("find", "寻车（闪灯 / 鸣笛）");
}

enum class ControlOutcome { IDLE, SUBMITTED, ACCEPTED, SUCCEEDED, FAILED, UNKNOWN, NOT_SENT }

data class VehicleControlSnapshot(
    val enabledActions: Set<VehicleAction> = emptySet(),
    val blockedReason: String = "等待 TBox 能力声明和有效车辆数据",
    val requestId: String? = null,
    val action: VehicleAction? = null,
    val outcome: ControlOutcome = ControlOutcome.IDLE,
) {
    val isPending: Boolean get() = outcome == ControlOutcome.SUBMITTED || outcome == ControlOutcome.ACCEPTED
}

/** Optional capability of an injected source; it shares that source's SOME/IP session. */
interface VehicleControl {
    fun controlSnapshot(): VehicleControlSnapshot
    fun requestControl(action: VehicleAction): Boolean
}
