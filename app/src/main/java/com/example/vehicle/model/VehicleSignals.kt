package com.example.vehicle.model

/** Transport-independent snapshot. No Android, Launcher or Car API dependencies.
 * sourceTimestampMs is the publisher's epoch time; never use it for local stream timeouts.
 * batteryPercent is the PoC percentage, NOT AAOS EV_BATTERY_LEVEL (energy in Wh).
 */
data class VehicleSignals(
    val sequence: Long,
    val sourceTimestampMs: Long,
    val speedMetersPerSecond: Double,
    val engineRpm: Int,
    val stateOfChargePercent: Int,
    val gear: SignalGear,
    val turnSignal: SignalTurn,
    val warning: SignalWarning,
    val validity: SignalValidity,
    val sourceStatus: SignalSourceStatus,
    val parkingBrake: Boolean,
    val doorLocked: Boolean,
    val seatBeltWarning: Boolean,
    val headlights: Int,
    val highBeam: Int,
    val coolantCelsius: Float,
    val batteryPercent: Float,
)

enum class SignalGear { PARK, REVERSE, NEUTRAL, DRIVE }
enum class SignalTurn { NONE, LEFT, RIGHT, HAZARD }
enum class SignalWarning { NONE, GENERAL, CRITICAL }
enum class SignalValidity { VALID, INVALID_SPEED, INCOMPLETE, STALE }
enum class SignalSourceStatus { NORMAL, INVALID, NO_DATA, SOURCE_DISCONNECTED, TRANSPORT_DISCONNECTED }
