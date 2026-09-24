package com.example.carlauncher.someip

/* Response health for the SOME/IP path. All timestamps use a monotonic clock. */
class SomeipConnectionMonitor {

    private var snapshot: Snapshot = Snapshot(State.STOPPED, false, null)
    private var responseBaselineMs: Long = 0

    enum class State {
        STOPPED,
        STARTING,
        UNAVAILABLE,
        WAITING_RESPONSE,
        ONLINE,
        RESPONSE_ERROR,
        RESPONSE_TIMEOUT,
        START_FAILED,
    }

    class Snapshot
    internal constructor(
        val state: State,
        val isAvailable:
            Boolean, /* Last response code in this availability period; null before any response. */
        val returnCode: Int?,
    )

    @Synchronized fun snapshot(): Snapshot = snapshot

    @Synchronized
    fun start() {
        snapshot = Snapshot(State.STARTING, false, null)
    }

    @Synchronized
    fun startFailed() {
        if (snapshot!!.state != State.STOPPED) {
            snapshot = Snapshot(State.START_FAILED, false, null)
        }
    }

    @Synchronized
    fun stop() {
        snapshot = Snapshot(State.STOPPED, false, null)
    }

    @Synchronized
    fun onAvailability(available: Boolean, nowMs: Long) {
        if (snapshot!!.state == State.STOPPED || snapshot!!.state == State.START_FAILED) return
        if (!available) {
            snapshot = Snapshot(State.UNAVAILABLE, false, null)
        } else if (!snapshot!!.isAvailable) {
            responseBaselineMs = nowMs
            snapshot = Snapshot(State.WAITING_RESPONSE, true, null)
        }
    }

    @Synchronized
    fun onResponse(ok: Boolean, returnCode: Int, nowMs: Long) {
        // Queued responses after an unavailable/stop event cannot restore ONLINE.
        if (!snapshot!!.isAvailable) return
        responseBaselineMs = nowMs
        snapshot =
            Snapshot(
                if (ok && returnCode == 0) State.ONLINE else State.RESPONSE_ERROR,
                true,
                returnCode,
            )
    }

    /* A decoded Event proves stream liveness without inventing a Method return code. */
    @Synchronized
    fun onEvent(nowMs: Long) {
        if (!snapshot!!.isAvailable) return
        responseBaselineMs = nowMs
        snapshot = Snapshot(State.ONLINE, true, null)
    }

    /* Returns true only on a timeout transition, including when no callbacks arrive. */
    @Synchronized
    fun checkTimeout(nowMs: Long): Boolean {
        if (
            (snapshot!!.isAvailable &&
                snapshot!!.state != State.RESPONSE_TIMEOUT &&
                nowMs - responseBaselineMs >= RESPONSE_TIMEOUT_MS)
        ) {
            snapshot = Snapshot(State.RESPONSE_TIMEOUT, true, snapshot!!.returnCode)
            return true
        }
        return false
    }

    companion object {
        const val RESPONSE_TIMEOUT_MS: Long = 3000
    }
}
