package com.example.carlauncher.ui

import android.content.Context
import com.example.carlauncher.R
import com.example.carlauncher.someip.SomeipConnectionMonitor

/* Localized display text; protocol values and state-machine labels remain unchanged. */
class CockpitText private constructor() {
    companion object {

        @JvmStatic
        fun state(context: Context?, value: String?): String? {
            when (value) {
                "START_FAILED" -> return context!!.getString(R.string.state_start_failed)
                "NO DATA" -> return context!!.getString(R.string.state_no_data)
                "INVALID" -> return context!!.getString(R.string.state_invalid)
                "ON" -> return context!!.getString(R.string.state_on)
                "OFF" -> return context!!.getString(R.string.state_off)
                "LOCKED" -> return context!!.getString(R.string.state_locked)
                "UNLOCKED" -> return context!!.getString(R.string.state_unlocked)
                "NONE" -> return context!!.getString(R.string.state_none)
                "GENERAL_WARNING" -> return context!!.getString(R.string.state_general_warning)
                "CRITICAL" -> return context!!.getString(R.string.state_critical)
                "LEFT" -> return context!!.getString(R.string.state_left)
                "RIGHT" -> return context!!.getString(R.string.state_right)
                "HAZARD" -> return context!!.getString(R.string.state_hazard)
                "VALID" -> return context!!.getString(R.string.state_valid)
                "INVALID_SPEED" -> return context!!.getString(R.string.state_invalid_speed)
                "INCOMPLETE" -> return context!!.getString(R.string.state_incomplete)
                "STALE" -> return context!!.getString(R.string.state_stale)
                "STOPPED" -> return context!!.getString(R.string.state_stopped)
                "CONNECTING" -> return context!!.getString(R.string.state_connecting)
                "CONNECTED" -> return context!!.getString(R.string.state_connected)
                "NO_DATA" -> return context!!.getString(R.string.state_no_data)
                "DISCONNECTED" -> return context!!.getString(R.string.state_disconnected)
                "ERROR" -> return context!!.getString(R.string.state_error)
                "STARTING" -> return context!!.getString(R.string.state_starting)
                "WAITING EVENT" -> return context!!.getString(R.string.state_waiting_event)
                "EVENT TIMEOUT" -> return context!!.getString(R.string.state_event_timeout)
                "ONLINE" -> return context!!.getString(R.string.state_online)
                "RESPONSE_ERROR" -> return context!!.getString(R.string.state_response_error)
                "AVAILABLE" -> return context!!.getString(R.string.state_available)
                "UNAVAILABLE" -> return context!!.getString(R.string.state_unavailable)
                else -> return value
            }
        }

        @JvmStatic
        private fun event(context: Context?, ui: CockpitUiState?): String? {
            when (ui!!.transportSnapshot!!.state) {
                SomeipConnectionMonitor.State.WAITING_RESPONSE ->
                    return context!!.getString(R.string.state_waiting_event)
                SomeipConnectionMonitor.State.RESPONSE_TIMEOUT ->
                    return context!!.getString(R.string.state_event_timeout)
                else -> return state(context, ui!!.transportSnapshot!!.state!!.name)
            }
        }

        @JvmStatic
        fun connection(context: Context?, ui: CockpitUiState?): String? {
            if (!ui!!.isServiceBound) return context!!.getString(R.string.service_unbound)
            if (
                (ui!!.transportSnapshot!!.state == SomeipConnectionMonitor.State.ONLINE &&
                    ui!!.state == CockpitConnectionState.INVALID_DATA)
            ) {
                return context!!.getString(R.string.connection_invalid)
            }
            return context!!.getString(R.string.connection_state, event(context, ui))
        }

        @JvmStatic
        fun transport(context: Context?, ui: CockpitUiState?): String? {
            if (!ui!!.isServiceBound) return context!!.getString(R.string.transport_stopped)
            return context!!.getString(
                R.string.transport_state,
                context!!.getString(
                    if (ui!!.transportSnapshot!!.isAvailable) R.string.state_available
                    else R.string.state_unavailable
                ),
                event(context, ui),
            )
        }
    }
}
