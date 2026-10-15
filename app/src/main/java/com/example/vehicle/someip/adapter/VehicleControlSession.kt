package com.example.vehicle.someip.adapter

import com.example.vehicle.control.*
import com.example.vehicle.model.*
import java.util.UUID
import org.json.JSONObject

/** Gateway v1 control policy. Owner serializes all calls with the vehicle adapter lock. */
internal class VehicleControlSession(
    private val now: () -> Long,
    private val send: (ByteArray) -> Boolean,
    private val changed: () -> Unit,
) {
    private var available = false
    private var challenge = UUID.randomUUID().toString()
    private var capabilities = emptySet<VehicleAction>()
    private var capabilitySequence = -1L
    private var capabilityUntil = 0L
    private var signals: VehicleSignals? = null
    private var signalAt = 0L
    private var deadline = 0L
    private var queriedAt = Long.MIN_VALUE
    private var result = VehicleControlSnapshot()

    fun onAvailability(value: Boolean) {
        if (!value) {
            if (result.isPending) result = result.copy(outcome = ControlOutcome.UNKNOWN)
            capabilities = emptySet()
            signals = null
            capabilitySequence = -1
            challenge = UUID.randomUUID().toString()
        }
        if (!available && value) queriedAt = Long.MIN_VALUE
        available = value
        changed()
    }

    fun onSignals(value: VehicleSignals) {
        signals = value
        signalAt = now()
        changed()
    }

    fun snapshot(): VehicleControlSnapshot {
        val reason = blockedReason()
        return result.copy(enabledActions = if (reason.isEmpty()) capabilities else emptySet(), blockedReason = reason)
    }

    private fun blockedReason(): String = when {
        !available -> "SOME/IP 服务不可用"
        result.isPending -> "等待上一条控制执行结果"
        now() >= capabilityUntil || capabilities.isEmpty() -> "TBox 未授权控制或能力声明已过期"
        signals == null || now() - signalAt >= 3000 -> "车辆数据缺失或已过期"
        signals?.validity != SignalValidity.VALID || signals?.sourceStatus != SignalSourceStatus.NORMAL -> "车辆数据无效"
        signals?.speedMetersPerSecond != 0.0 || signals?.gear != SignalGear.PARK || signals?.parkingBrake != true -> "仅允许静止、P 挡且驻车制动开启时控制"
        else -> ""
    }

    fun request(action: VehicleAction): Boolean {
        if (action !in snapshot().enabledActions) {
            if (!result.isPending) result = VehicleControlSnapshot(action = action, outcome = ControlOutcome.NOT_SENT)
            changed()
            return false
        }
        val id = UUID.randomUUID().toString()
        result = VehicleControlSnapshot(requestId = id, action = action, outcome = ControlOutcome.SUBMITTED)
        deadline = now() + 5000
        changed()
        val submitted = try {
            send(JSONObject().put("version", 1).put("type", "control")
                .put("requestId", id).put("challenge", challenge)
                .put("capabilitySeq", capabilitySequence).put("action", action.wireName)
                .toString().toByteArray(Charsets.UTF_8))
        } catch (error: Exception) {
            result = result.copy(outcome = ControlOutcome.UNKNOWN)
            changed()
            throw error
        }
        if (!submitted) result = result.copy(outcome = ControlOutcome.NOT_SENT)
        changed()
        return submitted
    }

    /** A discovery challenge binds capability leases to this connection, preventing stale replay. */
    fun queryCapabilities(): Boolean {
        queriedAt = now()
        return send(JSONObject().put("version", 1).put("type", "getCapabilities")
            .put("challenge", challenge).toString().toByteArray(Charsets.UTF_8))
    }

    fun onFrame(frame: JSONObject) {
        require(frame.integer("version") == 1L) { "Unsupported gateway version" }
        require(frame.getString("challenge") == challenge) { "Stale gateway session" }
        when (frame.getString("type")) {
            "capabilities" -> {
                val sequence = frame.integer("seq")
                require(sequence >= 0) { "Invalid capability sequence" }
                if (sequence <= capabilitySequence) return
                val lease = frame.integer("leaseMs")
                require(lease in 1..3000) { "Invalid capability lease" }
                val actions = frame.getJSONArray("actions")
                val names = (0 until actions.length()).map { actions.getString(it) }.toSet()
                capabilities = VehicleAction.entries.filter { it.wireName in names }.toSet()
                capabilitySequence = sequence
                capabilityUntil = now() + lease
            }
            "controlResult" -> {
                if (!result.isPending || now() >= deadline) return
                if (frame.getString("requestId") != result.requestId) return
                if (frame.getString("action") != result.action?.wireName) return
                val outcome = when (frame.getString("status")) {
                    "accepted" -> ControlOutcome.ACCEPTED
                    "succeeded" -> ControlOutcome.SUCCEEDED
                    "failed", "rejected" -> ControlOutcome.FAILED
                    else -> throw IllegalArgumentException("Unknown control result")
                }
                result = result.copy(outcome = outcome)
            }
            else -> throw IllegalArgumentException("Unknown gateway frame")
        }
        changed()
    }

    private fun JSONObject.integer(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "Invalid integer: $key" }
        return (value as Number).toLong()
    }

    fun checkTimeout() {
        if (result.isPending && now() >= deadline) {
            result = result.copy(outcome = ControlOutcome.UNKNOWN)
            changed()
        }
        if (available && (queriedAt == Long.MIN_VALUE || now() - queriedAt >= 1000)) queryCapabilities()
    }
}
