package com.zqautomotive.launcher.data.someip

import com.zqautomotive.launcher.data.DataStatus
import com.zqautomotive.launcher.model.*
import com.zqautomotive.vehicle.model.*
import kotlin.math.roundToInt

/** Launcher boundary: semantic units/enums become the existing display snapshot. */
object VehicleSignalMapper {
    fun toVehicleState(signal: VehicleSignals): VehicleState = VehicleState.Builder()
        .setVersion(1)
        .setSequence(signal.sequence)
        .setTimestampMs(signal.sourceTimestampMs)
        .setVehSpeedKph((signal.speedMetersPerSecond * 3.6).roundToInt())
        .setEngRpm(signal.engineRpm)
        .setSoc(signal.stateOfChargePercent)
        .setGear(when (signal.gear) {
            SignalGear.PARK -> Gear.P
            SignalGear.REVERSE -> Gear.R
            SignalGear.NEUTRAL -> Gear.N
            SignalGear.DRIVE -> Gear.D
        })
        .setTurnSignal(when (signal.turnSignal) {
            SignalTurn.NONE -> TurnSignal.NONE
            SignalTurn.LEFT -> TurnSignal.LEFT
            SignalTurn.RIGHT -> TurnSignal.RIGHT
            SignalTurn.HAZARD -> TurnSignal.HAZARD
        })
        .setWarning(when (signal.warning) {
            SignalWarning.NONE -> WarningState.NONE
            SignalWarning.GENERAL -> WarningState.GENERAL_WARNING
            SignalWarning.CRITICAL -> WarningState.CRITICAL
        })
        .setValidity(when (signal.validity) {
            SignalValidity.VALID -> DataValidity.VALID
            SignalValidity.INVALID_SPEED -> DataValidity.INVALID_SPEED
            SignalValidity.INCOMPLETE -> DataValidity.INCOMPLETE
            SignalValidity.STALE -> DataValidity.STALE
        })
        .setDataStatus(when (signal.sourceStatus) {
            SignalSourceStatus.NORMAL -> DataStatus.NORMAL
            SignalSourceStatus.INVALID -> DataStatus.INVALID
            SignalSourceStatus.NO_DATA -> DataStatus.NO_DATA
            SignalSourceStatus.SOURCE_DISCONNECTED -> DataStatus.SOURCE_DISCONNECTED
            SignalSourceStatus.TRANSPORT_DISCONNECTED -> DataStatus.TRANSPORT_DISCONNECTED
        })
        .setParkingBrake(signal.parkingBrake)
        .setDoorLock(signal.doorLocked)
        .setBeltWarning(signal.seatBeltWarning)
        .setHeadlightsState(signal.headlights)
        .setHighBeamLightsState(signal.highBeam)
        .setEngineCoolantTemp(signal.coolantCelsius)
        .setEvBatteryLevel(signal.batteryPercent)
        .build()
}
