package com.example.carlauncher.someip;

/** Response health for the SOME/IP path. All timestamps use a monotonic clock. */
public final class SomeipConnectionMonitor {
    public static final long RESPONSE_TIMEOUT_MS = 3000;

    public enum State {
        STOPPED, STARTING, UNAVAILABLE, WAITING_RESPONSE, ONLINE,
        RESPONSE_ERROR, RESPONSE_TIMEOUT, START_FAILED
    }

    public static final class Snapshot {
        private final State state;
        private final boolean available;
        private final Integer returnCode;

        private Snapshot(State state, boolean available, Integer returnCode) {
            this.state = state;
            this.available = available;
            this.returnCode = returnCode;
        }

        public State getState() { return state; }
        public boolean isAvailable() { return available; }
        /** Last response code in this availability period; null before any response. */
        public Integer getReturnCode() { return returnCode; }
    }

    private Snapshot snapshot = new Snapshot(State.STOPPED, false, null);
    private long responseBaselineMs;

    public synchronized Snapshot snapshot() { return snapshot; }

    public synchronized void start() {
        snapshot = new Snapshot(State.STARTING, false, null);
    }

    public synchronized void startFailed() {
        if (snapshot.state != State.STOPPED) {
            snapshot = new Snapshot(State.START_FAILED, false, null);
        }
    }

    public synchronized void stop() {
        snapshot = new Snapshot(State.STOPPED, false, null);
    }

    public synchronized void onAvailability(boolean available, long nowMs) {
        if (snapshot.state == State.STOPPED || snapshot.state == State.START_FAILED) return;
        if (!available) {
            snapshot = new Snapshot(State.UNAVAILABLE, false, null);
        } else if (!snapshot.available) {
            responseBaselineMs = nowMs;
            snapshot = new Snapshot(State.WAITING_RESPONSE, true, null);
        }
    }

    public synchronized void onResponse(boolean ok, int returnCode, long nowMs) {
        // Queued responses after an unavailable/stop event cannot restore ONLINE.
        if (!snapshot.available) return;
        responseBaselineMs = nowMs;
        snapshot = new Snapshot(ok && returnCode == 0 ? State.ONLINE : State.RESPONSE_ERROR,
                true, returnCode);
    }

    /** Returns true only on a timeout transition, including when no data callbacks arrive. */
    public synchronized boolean checkTimeout(long nowMs) {
        if (snapshot.available && snapshot.state != State.RESPONSE_TIMEOUT
                && nowMs - responseBaselineMs >= RESPONSE_TIMEOUT_MS) {
            snapshot = new Snapshot(State.RESPONSE_TIMEOUT, true, snapshot.returnCode);
            return true;
        }
        return false;
    }
}
