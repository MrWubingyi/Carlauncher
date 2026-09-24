package com.example.carlauncher.data.mock

import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.data.SourceType
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.data.VehicleDataSourceFactory
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.Gear
import com.example.carlauncher.model.VehicleState
import java.lang.reflect.Proxy
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** MockVehicleDataSource 的确定性测试：通过注入可控调度器，同步驱动 100ms 定时生成逻辑，覆盖正常/非法/静默/恢复四段模拟周期。 */
open class MockVehicleDataSourceTest {

    /* 可控调度器：捕获 scheduleWithFixedDelay 的任务，由测试手动 tick。 */
    private class ControllableScheduler {
        private val task: AtomicReference<Runnable?> = AtomicReference<Runnable?>()

        private val executor: ScheduledExecutorService? =
            Proxy.newProxyInstance(
                ControllableScheduler::class.java.getClassLoader(),
                arrayOf<Class<*>?>(ScheduledExecutorService::class.java),
            ) { proxy, method, args ->
                when (method!!.getName()) {
                    "scheduleWithFixedDelay" -> {
                        task.set(args!![0] as Runnable)
                        return@newProxyInstance null
                    }
                    "schedule",
                    "scheduleAtFixedRate" -> return@newProxyInstance null
                    "execute" -> {
                        (args!![0] as Runnable).run()
                        return@newProxyInstance null
                    }
                    "shutdownNow" -> return@newProxyInstance emptyList<Any?>()
                    "shutdown" -> return@newProxyInstance null
                    "isShutdown",
                    "isTerminated" -> return@newProxyInstance false
                    "awaitTermination" -> return@newProxyInstance false
                    else -> {
                        val returnType = method!!.getReturnType()
                        if (returnType == Boolean::class.javaPrimitiveType) {
                            return@newProxyInstance false
                        }
                        if (returnType == Int::class.javaPrimitiveType) {
                            return@newProxyInstance 0
                        }
                        if (returnType == Long::class.javaPrimitiveType) {
                            return@newProxyInstance 0L
                        }
                        if (List::class.java.isAssignableFrom(returnType)) {
                            return@newProxyInstance emptyList<Any?>()
                        }
                        if (ScheduledFuture::class.java.isAssignableFrom(returnType)) {
                            return@newProxyInstance null
                        }
                        return@newProxyInstance null
                    }
                }
            } as ScheduledExecutorService?

        internal fun scheduler(): ScheduledExecutorService? = executor

        internal fun tick() {
            val runnable = task.get()
            if (runnable == null) {
                throw IllegalStateException("start() 尚未调度任务")
            }
            runnable!!.run()
        }
    }

    /* 记录回调的监听器。 */
    private class RecordingListener : VehicleDataSource.Listener {
        internal val states: MutableList<VehicleState?> = ArrayList<VehicleState?>()
        internal val statuses: MutableList<DataSourceStatus?> = ArrayList<DataSourceStatus?>()
        internal val errors: MutableList<Exception?> = ArrayList<Exception?>()

        override fun onStateChanged(state: VehicleState?) {
            states.add(state)
        }

        override fun onSourceStatusChanged(status: DataSourceStatus?) {
            statuses.add(status)
        }

        override fun onError(exception: Exception?) {
            errors.add(exception)
        }
    }

    @Test
    open fun start_reportsConnected_andGeneratesNormalFrames() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()

        source.start(listener)

        assertTrue(source.isRunning)
        assertEquals(
            listOf<DataSourceStatus?>(DataSourceStatus.CONNECTED),
            listener.statuses,
        )

        scheduler.tick()

        assertEquals(1, listener.states.size.toLong())
        val state = listener.states.get(0)
        assertEquals(1L, state!!.sequence)
        assertEquals(2, state!!.vehSpeedKph.toLong()) // 0 -> +2
        assertEquals(Gear.D, state!!.gear)
        assertEquals(DataValidity.VALID, state!!.validity)
        assertEquals(DataStatus.NORMAL, state!!.dataStatus)
        assertEquals(70, state!!.soc.toLong())
        assertEquals(850, state!!.engRpm.toLong()) // 800 + 2 * 25
        assertEquals(0, listener.errors.size.toLong())
    }

    @Test
    open fun ticks_driveSpeedCycle_betweenZeroAndHundred() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()
        source.start(listener)

        // 第 50 帧：加速到 100 km/h 峰值
        for (i in 0..49) {
            scheduler.tick()
        }
        val peak = listener.states.get(49)
        assertEquals(100, peak!!.vehSpeedKph.toLong())
        assertEquals(Gear.D, peak!!.gear)
        assertEquals(3300, peak!!.engRpm.toLong()) // 800 + 100 * 25

        // 再走 50 帧：回到 0 km/h（P 档、怠速 800）
        for (i in 0..49) {
            scheduler.tick()
        }
        val valley = listener.states.get(99)
        assertEquals(0, valley!!.vehSpeedKph.toLong())
        assertEquals(Gear.P, valley!!.gear)
        assertEquals(800, valley!!.engRpm.toLong())
    }

    @Test
    open fun invalidWindow_emitsInvalidSpeedFrames() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()
        source.start(listener)

        // 第 600 帧进入 600-700 非法数据窗口
        for (i in 0..599) {
            scheduler.tick()
        }
        val invalid = listener.states.get(599)
        assertEquals(600L, invalid!!.sequence)
        assertEquals(255, invalid!!.vehSpeedKph.toLong()) // 超出 200 上限
        assertEquals(9999, invalid!!.engRpm.toLong())
        assertEquals(DataValidity.INVALID_SPEED, invalid!!.validity)
        assertEquals(DataStatus.INVALID, invalid!!.dataStatus)
    }

    @Test
    open fun silentWindow_reportsNoData_thenResumesConnected() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()
        source.start(listener)
        listener.statuses.clear()

        // 推进到第 699 帧（seq=699，仍在非法数据窗口内，无状态变更回调）
        for (i in 0..698) {
            scheduler.tick()
        }
        val statesBeforeSilence = listener.states.size
        assertEquals(0, listener.statuses.size.toLong())

        // 第 700 帧进入静默窗口：上报 NO_DATA，且不产生状态帧
        scheduler.tick()
        assertEquals(
            listOf<DataSourceStatus?>(DataSourceStatus.NO_DATA),
            listener.statuses,
        )
        assertEquals(statesBeforeSilence.toLong(), listener.states.size.toLong())

        // 静默窗口（seq 701..999）内不再产生任何回调
        for (i in 0..298) {
            scheduler.tick()
        }
        assertEquals(statesBeforeSilence.toLong(), listener.states.size.toLong())
        assertEquals(1, listener.statuses.size.toLong())

        // seq=1000 恢复正常：上报 CONNECTED 并继续产生帧
        scheduler.tick()
        assertEquals(2, listener.statuses.size.toLong())
        assertEquals(
            DataSourceStatus.CONNECTED,
            listener.statuses.get(listener.statuses.size - 1),
        )
        assertEquals((statesBeforeSilence + 1).toLong(), listener.states.size.toLong())
        val resumed = listener.states.get(listener.states.size - 1)
        assertEquals(1000L, resumed!!.sequence)
        assertEquals(DataValidity.VALID, resumed!!.validity)
    }

    @Test
    open fun start_whileAlreadyRunning_ignoresSecondListener() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val first = RecordingListener()
        val second = RecordingListener()

        source.start(first)
        source.start(second)
        assertTrue(source.isRunning)
        assertEquals(0, second.states.size.toLong())
        assertEquals(0, second.statuses.size.toLong())

        scheduler.tick()
        assertEquals(1, first.states.size.toLong())
        assertEquals(0, second.states.size.toLong())
    }

    @Test
    open fun stop_reportsStopped_andClearsRunningState() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()
        source.start(listener)
        listener.statuses.clear()

        source.stop()

        assertFalse(source.isRunning)
        assertEquals(
            listOf<DataSourceStatus?>(DataSourceStatus.STOPPED),
            listener.statuses,
        )
    }

    @Test
    open fun factory_mockSourceType_createsMockDataSource() {
        val dataSource = VehicleDataSourceFactory.create(null, SourceType.MOCK)
        assertSame(MockVehicleDataSource::class.java, dataSource!!.javaClass)
        assertFalse(dataSource!!.isRunning)
    }

    @Test
    open fun invalidWindow_lastFramePreservesRawBatteryAndClampsSoc() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()
        try {
            source.start(listener)
            for (i in 0..698) scheduler.tick()
            val last = listener.states.get(698)
            assertEquals(699L, last!!.sequence)
            assertEquals(100, last!!.soc.toLong())
            assertEquals(java.lang.Float.valueOf(150f), last!!.evBatteryLevel)
            assertEquals(java.lang.Float.valueOf(-999f), last!!.engineCoolantTemp)
            assertEquals(DataStatus.INVALID, last!!.dataStatus)
        } finally {
            source.stop()
        }
    }

    @Test
    open fun stop_twiceEmitsStoppedOnceAndDropsAlreadyQueuedNormalFrame() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()
        source.start(listener)
        scheduler.tick()
        source.stop()
        source.stop()
        scheduler.tick() // An executor task captured before shutdown can still be in flight.
        assertEquals(1, listener.states.size.toLong())
        assertEquals(
            java.util.Arrays.asList<DataSourceStatus?>(
                DataSourceStatus.CONNECTED,
                DataSourceStatus.STOPPED,
            ),
            listener.statuses,
        )
        assertFalse(source.isRunning)
    }

    @Test
    open fun stop_beforeQueuedSilenceTransitionDoesNotDereferenceClearedListener() {
        val scheduler = ControllableScheduler()
        val source = MockVehicleDataSource(scheduler.scheduler())
        val listener = RecordingListener()
        source.start(listener)
        for (i in 0..698) scheduler.tick()
        source.stop()
        scheduler.tick() // seq=700: same cancellation contract as an ordinary queued frame.
        assertEquals(699, listener.states.size.toLong())
        assertEquals(
            java.util.Arrays.asList<DataSourceStatus?>(
                DataSourceStatus.CONNECTED,
                DataSourceStatus.STOPPED,
            ),
            listener.statuses,
        )
    }
}
