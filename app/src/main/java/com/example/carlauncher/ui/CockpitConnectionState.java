package com.example.carlauncher.ui;

public enum CockpitConnectionState {
    ONLINE("STOP SEND", true, true),
    CONNECTING("PLEASE WAIT", false, false),
    INVALID_DATA("STOP SEND", true, true),
    DISCONNECTED("START SEND", true, false),
    RECOVERING("PLEASE WAIT", false, false);

    private final String actionLabel;
    private final boolean actionEnabled;
    private final boolean stopAction;

    CockpitConnectionState(String actionLabel, boolean actionEnabled, boolean stopAction) {
        this.actionLabel = actionLabel;
        this.actionEnabled = actionEnabled;
        this.stopAction = stopAction;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public boolean isActionEnabled() {
        return actionEnabled;
    }

    public boolean isStopAction() {
        return stopAction;
    }
}
