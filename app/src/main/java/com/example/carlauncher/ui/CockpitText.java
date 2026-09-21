package com.example.carlauncher.ui;

import android.content.Context;
import com.example.carlauncher.R;
import com.example.carlauncher.someip.SomeipConnectionMonitor;

/** Localized display text; protocol values and state-machine labels remain unchanged. */
public final class CockpitText {
    private CockpitText() {}

    public static String state(Context context, String value) {
        switch (value) {
            case "START_FAILED": return context.getString(R.string.state_start_failed);
            case "NO DATA": return context.getString(R.string.state_no_data);
            case "INVALID": return context.getString(R.string.state_invalid);
            case "ON": return context.getString(R.string.state_on);
            case "OFF": return context.getString(R.string.state_off);
            case "LOCKED": return context.getString(R.string.state_locked);
            case "UNLOCKED": return context.getString(R.string.state_unlocked);
            case "NONE": return context.getString(R.string.state_none);
            case "GENERAL_WARNING": return context.getString(R.string.state_general_warning);
            case "CRITICAL": return context.getString(R.string.state_critical);
            case "LEFT": return context.getString(R.string.state_left);
            case "RIGHT": return context.getString(R.string.state_right);
            case "HAZARD": return context.getString(R.string.state_hazard);
            case "VALID": return context.getString(R.string.state_valid);
            case "INVALID_SPEED": return context.getString(R.string.state_invalid_speed);
            case "INCOMPLETE": return context.getString(R.string.state_incomplete);
            case "STALE": return context.getString(R.string.state_stale);
            case "STOPPED": return context.getString(R.string.state_stopped);
            case "CONNECTING": return context.getString(R.string.state_connecting);
            case "CONNECTED": return context.getString(R.string.state_connected);
            case "NO_DATA": return context.getString(R.string.state_no_data);
            case "DISCONNECTED": return context.getString(R.string.state_disconnected);
            case "ERROR": return context.getString(R.string.state_error);
            case "STARTING": return context.getString(R.string.state_starting);
            case "WAITING EVENT": return context.getString(R.string.state_waiting_event);
            case "EVENT TIMEOUT": return context.getString(R.string.state_event_timeout);
            case "ONLINE": return context.getString(R.string.state_online);
            case "RESPONSE_ERROR": return context.getString(R.string.state_response_error);
            case "AVAILABLE": return context.getString(R.string.state_available);
            case "UNAVAILABLE": return context.getString(R.string.state_unavailable);
            default: return value;
        }
    }

    private static String event(Context context, CockpitUiState ui) {
        switch (ui.getTransportSnapshot().getState()) {
            case WAITING_RESPONSE: return context.getString(R.string.state_waiting_event);
            case RESPONSE_TIMEOUT: return context.getString(R.string.state_event_timeout);
            default: return state(context, ui.getTransportSnapshot().getState().name());
        }
    }

    public static String connection(Context context, CockpitUiState ui) {
        if (!ui.isServiceBound()) return context.getString(R.string.service_unbound);
        if (ui.getTransportSnapshot().getState() == SomeipConnectionMonitor.State.ONLINE
                && ui.getState() == CockpitConnectionState.INVALID_DATA) {
            return context.getString(R.string.connection_invalid);
        }
        return context.getString(R.string.connection_state, event(context, ui));
    }

    public static String transport(Context context, CockpitUiState ui) {
        if (!ui.isServiceBound()) return context.getString(R.string.transport_stopped);
        return context.getString(R.string.transport_state,
                context.getString(ui.getTransportSnapshot().isAvailable()
                        ? R.string.state_available : R.string.state_unavailable), event(context, ui));
    }
}
