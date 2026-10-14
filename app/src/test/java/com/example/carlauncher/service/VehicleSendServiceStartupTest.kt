package com.example.carlauncher.service

import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.data.someip.VSomeIpDataSource
import com.example.vehicle.someip.adapter.SomeipConnectionMonitor
import com.example.vehicle.someip.transport.NativeVehicleTransport
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

/** Tests Service orchestration only; Android scheduling/notifications are not simulated here. */
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

    private class Transport : NativeVehicleTransport {
        var starts = 0
        var stops = 0
        var succeeds = true
        var stopError: Throwable? = null
        override val isAvailable = false
        override fun setListener(listener: NativeVehicleTransport.Listener?) = Unit
        override fun start(): Boolean { starts++; return succeeds }
        override fun stop() { stops++; stopError?.let { throw it } }
    }

    private val service = VehicleSendService()
    private val executor = QueuedExecutor()
    private val transport = Transport()
    private val monitor = field("someipMonitor").get(service) as SomeipConnectionMonitor

    private fun field(name: String) = VehicleSendService::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun schedule(
        source: VSomeIpDataSource? = VSomeIpDataSource(transport, monitor),
        availableExecutor: QueuedExecutor? = executor,
    ): VSomeIpDataSource? {
        field("vsomeipDataSource").set(service, source)
        field("vsomeipExecutor").set(service, availableExecutor)
        field("sourceStatus").set(service, DataSourceStatus.CONNECTING)
        VehicleSendService::class.java.getDeclaredMethod("startVsomeipClient").apply {
            isAccessible = true
        }.invoke(service)
        return source
    }

    private fun assertFailed() {
        assertEquals(DataSourceStatus.ERROR, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.START_FAILED, service.someipStatus!!.state)
        assertNull(service.getLatestVehicleState())
    }

    @Test fun startsOnlyOnExecutorAndPreservesConnectingUntilDataArrives() {
        val source = schedule()!!
        assertEquals(0, transport.starts)
        executor.drain()
        assertEquals(1, transport.starts)
        assertTrue(source.isRunning)
        assertEquals(DataSourceStatus.CONNECTING, service.getSourceStatus())
        source.stop()
    }

    @Test fun failedReturnIsNotTreatedAsSuccessfulStart() {
        transport.succeeds = false
        val source = schedule()!!
        executor.drain()
        assertFailed()
        assertFalse(source.isRunning)
        assertEquals(1, transport.stops)
    }

    @Test fun missingExecutorReportsFailureBeforeAdapterStarts() {
        schedule(availableExecutor = null)
        assertFailed()
        assertEquals(0, transport.starts)
    }

    @Test fun shutdownExecutorReportsFailureBeforeAdapterStarts() {
        executor.shutdown()
        schedule()
        assertFailed()
        assertEquals(0, transport.starts)
    }

    @Test fun submissionRejectionReportsFailure() {
        executor.reject = true
        schedule()
        assertFailed()
        assertEquals(0, transport.starts)
    }

    @Test fun missingSourceReportsFailure() {
        schedule(source = null)
        assertFailed()
        assertTrue(executor.tasks.isEmpty())
    }

    @Test fun exceptionAfterPartialStartCleansUpBeforeReportingFailure() {
        val source = object : VSomeIpDataSource(transport, monitor) {
            override fun start(listener: VehicleDataSource.Listener?) {
                super.start(listener)
                throw IllegalStateException("failure after transport start")
            }
        }
        schedule(source)
        executor.drain()
        assertFailed()
        assertFalse(source.isRunning)
        assertEquals(1, transport.stops)
    }

    @Test fun nativeLoadFailureAndCleanupFailureUseTheSameTerminalResult() {
        val failure = UnsatisfiedLinkError("test native load failure")
        val cleanupFailure = IllegalStateException("test cleanup failure")
        transport.stopError = cleanupFailure
        val source = object : VSomeIpDataSource(transport, monitor) {
            override fun start(listener: VehicleDataSource.Listener?) {
                super.start(listener)
                throw failure
            }
        }
        schedule(source)
        executor.drain()
        assertFailed()
        assertFalse(source.isRunning)
        assertArrayEquals(arrayOf(cleanupFailure), failure.suppressed)
    }

    @Test fun destructionBeforeQueuedStartPreventsTransportStartup() {
        schedule()
        service.onDestroy()
        executor.drain()
        assertEquals(0, transport.starts)
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.STOPPED, service.someipStatus!!.state)
    }

    @Test fun queuedDestructionContainsStopRuntimeException() {
        assertStopFailureIsContained(IllegalStateException("test cleanup failure"))
    }

    @Test fun queuedDestructionContainsNativeLinkageFailure() {
        assertStopFailureIsContained(UnsatisfiedLinkError("test native cleanup failure"))
    }

    @Test fun queuedDestructionPropagatesTheOriginalVirtualMachineError() {
        // Construct the failure directly; this test does not create memory pressure.
        val failure = OutOfMemoryError("simulated VM failure")
        val source = queueDestructionWithStopFailure(failure)

        val thrown = assertThrows(OutOfMemoryError::class.java) { executor.drain() }

        assertSame(failure, thrown)
        assertEquals(1, transport.stops)
        assertFalse(source.isRunning)
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.STOPPED, service.someipStatus!!.state)
    }

    private fun queueDestructionWithStopFailure(failure: Throwable): VSomeIpDataSource {
        val source = schedule()!!
        executor.drain()
        assertTrue(source.isRunning)
        transport.stopError = failure

        service.onDestroy()

        assertEquals(0, transport.stops)
        assertEquals(1, executor.tasks.size)
        return source
    }

    private fun assertStopFailureIsContained(failure: Throwable) {
        val source = queueDestructionWithStopFailure(failure)
        var nextLifecycleTaskRan = false
        executor.execute { nextLifecycleTaskRan = true }

        executor.drain()

        assertEquals(1, transport.stops)
        assertFalse(source.isRunning)
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.STOPPED, service.someipStatus!!.state)
        assertTrue(nextLifecycleTaskRan)
    }

    @Test fun failureAfterDestructionDoesNotOverwriteStoppedState() {
        val source = object : VSomeIpDataSource(transport, monitor) {
            override fun start(listener: VehicleDataSource.Listener?) {
                service.onDestroy()
                listener?.onSourceStatusChanged(DataSourceStatus.CONNECTING)
                listener?.onError(IllegalStateException("late startup callback"))
                throw IllegalStateException("late start failure")
            }
        }
        schedule(source)
        executor.drain()
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus())
        assertEquals(SomeipConnectionMonitor.State.STOPPED, service.someipStatus!!.state)
    }
}
