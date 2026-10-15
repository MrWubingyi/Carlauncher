package com.example.someip.internal

import com.example.someip.SomeIpClient
import com.example.someip.SomeIpClientConfig
import java.util.ArrayDeque

/** Android-free lifecycle used by production and JVM tests. */
internal class SessionCoordinator(
    private val backend: Backend,
    private val dispatcher: Dispatcher,
) {
    internal interface Backend {
        fun ensureLoaded()
        fun start(config: SomeIpClientConfig): Boolean
        fun stop()
        fun send(payload: ByteArray): Boolean
        fun isAvailable(): Boolean
    }

    internal fun interface Dispatcher {
        fun post(callback: () -> Unit)
    }

    internal fun interface Resources {
        /** A failed acquisition must release anything it acquired before throwing. */
        fun acquire(config: SomeIpClientConfig): AutoCloseable
    }

    private class Session(
        val owner: Any,
        val generation: Long,
        val config: SomeIpClientConfig,
        val listener: SomeIpClient.Listener,
    ) {
        var resources: AutoCloseable? = null
        var started = false
        val pending = ArrayDeque<() -> Unit>()
        var dispatchScheduled = false
    }

    // Native joins and platform unregister run under operations, never under stateLock.
    // Callback threads only take stateLock, so they cannot deadlock native stop/unregister.
    private val operations = Any()
    private val stateLock = Any()
    private var generation = 0L
    private var active: Session? = null

    fun create(config: SomeIpClientConfig, resources: Resources): SomeIpClient =
        object : SomeIpClient {
            override val isAvailable: Boolean
                get() = synchronized(operations) {
                    val ownsSession = synchronized(stateLock) {
                        active?.let { it.owner === this && it.started } == true
                    }
                    ownsSession && backend.isAvailable()
                }

            override fun start(listener: SomeIpClient.Listener): Boolean =
                start(this, config, resources, listener)

            override fun stop() {
                stop(this)
            }

            override fun send(payload: ByteArray): Boolean = synchronized(operations) {
                val canSend = synchronized(stateLock) {
                    active?.let {
                        it.owner === this && it.started &&
                            payload.size <= it.config.service.maxRequestPayloadBytes
                    } == true
                }
                canSend && backend.send(payload)
            }
        }

    private fun start(
        owner: Any,
        config: SomeIpClientConfig,
        resources: Resources,
        listener: SomeIpClient.Listener,
    ): Boolean = synchronized(operations) {
        val session = synchronized(stateLock) {
            active?.let { return it.owner === owner && it.started }
            Session(owner, ++generation, config, listener).also { active = it }
        }
        var nativeAttempted = false
        try {
            backend.ensureLoaded()
            session.resources = resources.acquire(config)
            nativeAttempted = true
            if (!backend.start(config)) return false
            val dispatchPending = synchronized(stateLock) {
                session.started = true
                scheduleIfNeeded(session)
            }
            if (dispatchPending) dispatcher.post { drain(session) }
            return true
        } finally {
            if (!session.started) {
                synchronized(stateLock) {
                    active = null
                    generation++
                }
                try {
                    session.resources?.close()
                } finally {
                    if (nativeAttempted) backend.stop()
                }
            }
        }
    }

    private fun stop(owner: Any): Unit = synchronized(operations) {
        val stoppedGeneration: Long
        val session = synchronized(stateLock) {
            val current = active
            if (current == null || current.owner !== owner) return
            active = null
            stoppedGeneration = ++generation
            current
        }
        try {
            try {
                session.resources?.close()
            } finally {
                backend.stop()
            }
        } finally {
            dispatcher.post {
                val deliver = synchronized(stateLock) { generation == stoppedGeneration }
                if (deliver) session.listener.onStopped()
            }
        }
        Unit
    }

    fun onNativeEvent(kind: Int, argument: Int) {
        val session = synchronized(stateLock) { active } ?: return
        enqueue(session) {
            when (kind) {
                REGISTERED -> session.listener.onRegistered()
                AVAILABLE -> session.listener.onAvailabilityChanged(true)
                UNAVAILABLE -> session.listener.onAvailabilityChanged(false)
                RESPONSE_OK -> session.listener.onResponse(true, argument)
                RESPONSE_ERROR -> session.listener.onResponse(false, argument)
                // Never join the native worker on its own callback thread.
                STOPPED -> stop(session.owner)
            }
        }
    }

    fun onNativePayload(payload: ByteArray) {
        val session = synchronized(stateLock) { active } ?: return
        if (payload.size > session.config.service.maxEventPayloadBytes) return
        enqueue(session) { session.listener.onPayload(payload) }
    }

    private fun enqueue(session: Session, callback: () -> Unit) {
        val dispatchPending = synchronized(stateLock) {
            if (active !== session || generation != session.generation) return
            session.pending.addLast(callback)
            scheduleIfNeeded(session)
        }
        if (dispatchPending) dispatcher.post { drain(session) }
    }

    // Called under stateLock. Early native callbacks remain buffered until start succeeds.
    private fun scheduleIfNeeded(session: Session): Boolean {
        if (!session.started || session.dispatchScheduled || session.pending.isEmpty()) return false
        session.dispatchScheduled = true
        return true
    }

    private fun drain(session: Session) {
        repeat(CALLBACK_BATCH_SIZE) {
            val callback = synchronized(stateLock) {
                if (active !== session || generation != session.generation) {
                    session.pending.clear()
                }
                if (session.pending.isEmpty()) {
                    session.dispatchScheduled = false
                    return
                }
                session.pending.removeFirst()
            }
            callback()
        }
        val dispatchRemaining = synchronized(stateLock) {
            if (active !== session || generation != session.generation) session.pending.clear()
            if (session.pending.isEmpty()) {
                session.dispatchScheduled = false
                false
            } else {
                true
            }
        }
        // Yield to input/rendering and other main-Looper tasks even under a sustained event stream.
        if (dispatchRemaining) dispatcher.post { drain(session) }
    }

    private companion object {
        const val CALLBACK_BATCH_SIZE = 32
        const val REGISTERED = 0
        const val AVAILABLE = 1
        const val UNAVAILABLE = 2
        const val RESPONSE_OK = 3
        const val RESPONSE_ERROR = 4
        const val STOPPED = 5
    }
}
