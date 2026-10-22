package com.zqautomotive.launcher.service

import com.zqautomotive.launcher.data.DataSourceStatus
import com.zqautomotive.launcher.data.VehicleDataSource
import com.zqautomotive.launcher.data.VehicleEventSourceFactory
import com.zqautomotive.launcher.model.VehicleState
import com.zqautomotive.vehicle.someip.adapter.SomeipConnectionMonitor
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Tests injected interfaces without native transport, concrete sources or reflective injection. */
class VehicleSendServiceStartupTest {
    private class QueuedExecutor : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        var closed = false
        var reject = false
        override fun execute(command: Runnable) {
            if (closed || reject) throw RejectedExecutionException("test rejection")
            tasks.addLast(command)
        }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        override fun shutdown() { closed = true }
        override fun shutdownNow(): MutableList<Runnable> {
            closed = true
            return tasks.toMutableList().also { tasks.clear() }
        }
        override fun isShutdown() = closed
        override fun isTerminated() = closed && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    private class Source : VehicleDataSource {
        var starts = 0
        var stops = 0
        var succeeds = true
        var startError: Throwable? = null
        var stopError: Throwable? = null
        var onStart: () -> Unit = {}
        var onStop: () -> Unit = {}
        var listener: VehicleDataSource.Listener? = null
        override var isRunning = false
        override fun start(listener: VehicleDataSource.Listener?) {
            starts++
            this.listener = listener
            isRunning = succeeds
            onStart()
            startError?.let { throw it }
        }
        override fun stop() {
            stops++
            isRunning = false
            onStop()
            stopError?.let { throw it }
        }
    }

    private val service = VehicleSendService()
    private val executor = QueuedExecutor()
    private val source = Source()
    private var creations = 0
    private val factory = VehicleEventSourceFactory { context, _ ->
        assertSame(service, context)
        creations++
        source
    }

    private fun schedule(availableExecutor: QueuedExecutor? = executor) =
        service.startDataSource(factory, availableExecutor)

    private fun assertFailed() {
        assertEquals(DataSourceStatus.ERROR, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.START_FAILED, service.someipStatus!!.state)
        assertNull(service.getLatestVehicleState())
    }

    @Test fun createsAndStartsOnlyOnExecutorAndPreservesConnectingUntilDataArrives() {
        schedule()
        assertEquals(0, creations)
        assertEquals(0, source.starts)
        executor.drain()
        assertEquals(1, creations)
        assertEquals(1, source.starts)
        assertTrue(source.isRunning)
        assertEquals(DataSourceStatus.CONNECTING, service.getSourceStatus())
        service.onDestroy()
        executor.drain()
    }

    @Test fun sourceThatRemainsStoppedIsNotTreatedAsSuccessfulStart() {
        source.succeeds = false
        schedule()
        executor.drain()
        assertFailed()
        assertFalse(source.isRunning)
        assertEquals(1, source.stops)
    }

    @Test fun missingExecutorReportsFailureWithoutCreatingSource() {
        schedule(availableExecutor = null)
        assertFailed()
        assertEquals(0, creations)
    }

    @Test fun shutdownExecutorReportsFailureWithoutCreatingSource() {
        executor.shutdown()
        schedule()
        assertFailed()
        assertEquals(0, creations)
    }

    @Test fun submissionRejectionReportsFailureWithoutCreatingSource() {
        executor.reject = true
        schedule()
        assertFailed()
        assertEquals(0, creations)
    }

    @Test fun factoryExceptionUsesTheSameTerminalFailure() {
        service.startDataSource(VehicleEventSourceFactory { _, _ ->
            throw IllegalStateException("creation failed")
        }, executor)
        executor.drain()
        assertFailed()
        assertEquals(0, source.starts)
    }

    @Test fun factoryLinkageFailureUsesTheSameTerminalFailure() {
        service.startDataSource(VehicleEventSourceFactory { _, _ ->
            throw UnsatisfiedLinkError("creation linkage failure")
        }, executor)
        executor.drain()
        assertFailed()
    }

    @Test fun exceptionAfterPartialStartCleansUpBeforeReportingFailure() {
        source.startError = IllegalStateException("failure after source start")
        source.onStop = { assertEquals(DataSourceStatus.CONNECTING, service.getSourceStatus()) }
        schedule()
        executor.drain()
        assertFailed()
        assertFalse(source.isRunning)
        assertEquals(1, source.stops)
    }

    @Test fun nativeLoadFailureAndCleanupFailureUseTheSameTerminalResult() {
        val failure = UnsatisfiedLinkError("test native load failure")
        val cleanupFailure = IllegalStateException("test cleanup failure")
        source.startError = failure
        source.stopError = cleanupFailure
        schedule()
        executor.drain()
        assertFailed()
        assertFalse(source.isRunning)
        assertArrayEquals(arrayOf(cleanupFailure), failure.suppressed)
    }

    @Test fun destructionBeforeQueuedStartPreventsSourceCreation() {
        schedule()
        service.onDestroy()
        executor.drain()
        assertEquals(0, creations)
        assertEquals(0, source.starts)
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.STOPPED, service.someipStatus!!.state)
    }

    @Test fun destructionDuringCreationReleasesReturnedSourceWithoutStartingIt() {
        service.startDataSource(VehicleEventSourceFactory { _, _ ->
            service.onDestroy()
            source
        }, executor)
        executor.drain()
        assertEquals(0, source.starts)
        assertEquals(1, source.stops)
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
    }

    @Test fun callbacksPublishStateAndAreIgnoredAfterDestruction() {
        schedule()
        executor.drain()
        val state = VehicleState.Builder().build()
        source.listener!!.onStateChanged(state)
        source.listener!!.onSourceStatusChanged(DataSourceStatus.CONNECTED)
        assertSame(state, service.getLatestVehicleState())
        assertEquals(DataSourceStatus.CONNECTED, service.getSourceStatus())
        service.onDestroy()
        source.listener!!.onStateChanged(VehicleState.Builder().build())
        source.listener!!.onSourceStatusChanged(DataSourceStatus.CONNECTED)
        source.listener!!.onError(IllegalStateException("late callback"))
        executor.drain()
        assertSame(state, service.getLatestVehicleState())
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
    }

    @Test fun queuedDestructionContainsStopRuntimeException() {
        assertStopFailureIsContained(IllegalStateException("test cleanup failure"))
    }

    @Test fun queuedDestructionContainsNativeLinkageFailure() {
        assertStopFailureIsContained(UnsatisfiedLinkError("test native cleanup failure"))
    }

    @Test fun queuedDestructionPropagatesTheOriginalVirtualMachineError() {
        val failure = OutOfMemoryError("simulated VM failure")
        queueDestructionWithStopFailure(failure)
        val thrown = assertThrows(OutOfMemoryError::class.java) { executor.drain() }
        assertSame(failure, thrown)
        assertEquals(1, source.stops)
        assertFalse(source.isRunning)
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
    }

    private fun queueDestructionWithStopFailure(failure: Throwable) {
        schedule()
        executor.drain()
        source.stopError = failure
        service.onDestroy()
        assertEquals(0, source.stops)
        assertEquals(1, executor.tasks.size)
    }

    private fun assertStopFailureIsContained(failure: Throwable) {
        queueDestructionWithStopFailure(failure)
        var nextLifecycleTaskRan = false
        executor.execute { nextLifecycleTaskRan = true }
        executor.drain()
        assertEquals(1, source.stops)
        assertFalse(source.isRunning)
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.STOPPED, service.someipStatus!!.state)
        assertTrue(nextLifecycleTaskRan)
    }

    @Test fun failureAfterDestructionDoesNotOverwriteStoppedState() {
        source.onStart = {
            service.onDestroy()
            source.listener!!.onSourceStatusChanged(DataSourceStatus.CONNECTING)
            source.listener!!.onError(IllegalStateException("late startup callback"))
            throw IllegalStateException("late start failure")
        }
        schedule()
        executor.drain()
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.STOPPED, service.someipStatus!!.state)
    }

    @Test fun sharedExecutorReleasesOldSourceBeforeCreatingTheNextServiceSource() {
        val events = mutableListOf<String>()
        source.onStop = { events += "old stopped" }
        schedule()
        executor.drain()
        service.onDestroy()
        val next = VehicleSendService()
        next.startDataSource(VehicleEventSourceFactory { _, _ ->
            events += "new created"
            Source().apply { onStart = { events += "new started" } }
        }, executor)
        executor.drain()
        assertEquals(listOf("old stopped", "new created", "new started"), events)
        next.onDestroy()
        executor.drain()
    }
}
