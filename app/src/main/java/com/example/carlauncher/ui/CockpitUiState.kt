package com.example.carlauncher.ui

import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.someip.SomeipConnectionMonitor
import com.example.carlauncher.someip.SomeipConnectionMonitor.Snapshot

/** Immutable cockpit snapshot. Transport health is determined only by SOME/IP. */
class CockpitUiState(
    val vehicleState: VehicleState?,
    val dataSourceStatus: DataSourceStatus?,
    val isServiceBound: Boolean,
    val transportSnapshot: Snapshot?,
    validity: DataValidity?,
) {
    val state: CockpitConnectionState

    val connectionLabel: String
        get() {
            if (!isServiceBound) return "SERVICE UNBOUND"
            if (
                (transportSnapshot!!.state == SomeipConnectionMonitor.State.ONLINE &&
                    state == CockpitConnectionState.INVALID_DATA)
            ) {
                return "SOME/IP ONLINE / INVALID DATA"
            }
            return "SOME/IP " + eventStateLabel()!!
        }

    // Service ownership, rather than network health, determines whether STOP is possible.
    val actionLabel: String
        get() {
            return if (isServiceBound) "STOP RECEIVE" else "START RECEIVE"
        }

    val isActionEnabled: Boolean
        get() {
            return true
        }

    val transportLabel: String
        get() {
            if (!isServiceBound) return "SOME/IP: STOPPED"
            return ("SOME/IP: " +
                (if (transportSnapshot!!.isAvailable) "AVAILABLE" else "UNAVAILABLE") +
                " | " +
                eventStateLabel() +
                " | EVENT 0x8001")
        }

    val connectionState: String
        get() {
            return if (
                isServiceBound && transportSnapshot!!.state == SomeipConnectionMonitor.State.ONLINE
            )
                "Connection: ONLINE"
            else "Connection: OFFLINE"
        }

    init {
        this.state =
            if (isServiceBound) mapState(validity, transportSnapshot)
            else CockpitConnectionState.DISCONNECTED
    }

    fun hasVehicleData(): Boolean = vehicleState != null

    fun isStopAction(): Boolean = isServiceBound

    private fun eventStateLabel(): String {
        when (transportSnapshot!!.state) {
            SomeipConnectionMonitor.State.WAITING_RESPONSE -> return "WAITING EVENT"
            SomeipConnectionMonitor.State.RESPONSE_TIMEOUT -> return "EVENT TIMEOUT"
            else -> return transportSnapshot!!.state!!.name
        }
    }

    companion object {

        @JvmStatic
        private fun mapState(validity: DataValidity?, someip: Snapshot?): CockpitConnectionState {
            when (someip!!.state) {
                SomeipConnectionMonitor.State.STARTING -> return CockpitConnectionState.CONNECTING
                SomeipConnectionMonitor.State.WAITING_RESPONSE ->
                    return CockpitConnectionState.RECOVERING
                SomeipConnectionMonitor.State.ONLINE ->
                    return if (validity == DataValidity.VALID) CockpitConnectionState.ONLINE
                    else CockpitConnectionState.INVALID_DATA
                SomeipConnectionMonitor.State.RESPONSE_ERROR ->
                    return CockpitConnectionState.INVALID_DATA
                else -> return CockpitConnectionState.DISCONNECTED
            }
        }

        @JvmStatic
        fun initial(): CockpitUiState =
            CockpitUiState(
                null,
                DataSourceStatus.STOPPED,
                false,
                SomeipConnectionMonitor().snapshot(),
                DataValidity.VALID,
            )
    }
}
