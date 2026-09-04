package com.example.carlauncher.data.mock;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.DataStatus;
import com.example.carlauncher.data.SourceType;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.data.VehicleDataSourceFactory;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.Gear;
import com.example.carlauncher.model.VehicleState;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * MockVehicleDataSource 的确定性测试：通过注入可控调度器，同步驱动
 * 100ms 定时生成逻辑，覆盖正常/非法/静默/恢复四段模拟周期。
 */
public class MockVehicleDataSourceTest {

    /** 可控调度器：捕获 scheduleWithFixedDelay 的任务，由测试手动 tick。 */
    private static final class ControllableScheduler {
        private final AtomicReference<Runnable> task = new AtomicReference<>();

        private final ScheduledExecutorService executor =
                (ScheduledExecutorService) Proxy.newProxyInstance(
                        ControllableScheduler.class.getClassLoader(),
                        new Class<?>[]{ScheduledExecutorService.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "scheduleWithFixedDelay":
                                    task.set((Runnable) args[0]);
                                    return null;
                                case "schedule":
                                case "scheduleAtFixedRate":
                                    return null;
                                case "execute":
                                    ((Runnable) args[0]).run();
                                    return null;
                                case "shutdownNow":
                                    return Collections.emptyList();
                                case "shutdown":
                                    return null;
                                case "isShutdown":
                                case "isTerminated":
                                    return false;
                                case "awaitTermination":
                                    return false;
                                default: {
                                    Class<?> returnType = method.getReturnType();
                                    if (returnType == boolean.class) {
                                        return false;
                                    }
                                    if (returnType == int.class) {
                                        return 0;
                                    }
                                    if (returnType == long.class) {
                                        return 0L;
                                    }
                                    if (List.class.isAssignableFrom(returnType)) {
                                        return Collections.emptyList();
                                    }
                                    if (ScheduledFuture.class.isAssignableFrom(returnType)) {
                                        return null;
                                    }
                                    return null;
                                }
                            }
                        });

        ScheduledExecutorService scheduler() {
            return executor;
        }

        void tick() {
            Runnable runnable = task.get();
            if (runnable == null) {
                throw new IllegalStateException("start() 尚未调度任务");
            }
            runnable.run();
        }
    }

    /** 记录回调的监听器。 */
    private static final class RecordingListener implements VehicleDataSource.Listener {
        final List<VehicleState> states = new ArrayList<>();
        final List<DataSourceStatus> statuses = new ArrayList<>();
        final List<Exception> errors = new ArrayList<>();

        @Override
        public void onStateChanged(VehicleState state) {
            states.add(state);
        }

        @Override
        public void onSourceStatusChanged(DataSourceStatus status) {
            statuses.add(status);
        }

        @Override
        public void onError(Exception exception) {
            errors.add(exception);
        }
    }

    @Test
    public void start_reportsConnected_andGeneratesNormalFrames() {
        ControllableScheduler scheduler = new ControllableScheduler();
        MockVehicleDataSource source = new MockVehicleDataSource(scheduler.scheduler());
        RecordingListener listener = new RecordingListener();

        source.start(listener);

        assertTrue(source.isRunning());
        assertEquals(Collections.singletonList(DataSourceStatus.CONNECTED),
                listener.statuses);

        scheduler.tick();

        assertEquals(1, listener.states.size());
        VehicleState state = listener.states.get(0);
        assertEquals(1L, state.getSequence());
        assertEquals(2, state.getVehSpeedKph());   // 0 -> +2
        assertEquals(Gear.D, state.getGear());
        assertEquals(DataValidity.VALID, state.getValidity());
        assertEquals(DataStatus.NORMAL, state.getDataStatus());
        assertEquals(70, state.getSoc());
        assertEquals(850, state.getEngRpm());      // 800 + 2 * 25
        assertEquals(0, listener.errors.size());
    }

    @Test
    public void ticks_driveSpeedCycle_betweenZeroAndHundred() {
        ControllableScheduler scheduler = new ControllableScheduler();
        MockVehicleDataSource source = new MockVehicleDataSource(scheduler.scheduler());
        RecordingListener listener = new RecordingListener();
        source.start(listener);

        // 第 50 帧：加速到 100 km/h 峰值
        for (int i = 0; i < 50; i++) {
            scheduler.tick();
        }
        VehicleState peak = listener.states.get(49);
        assertEquals(100, peak.getVehSpeedKph());
        assertEquals(Gear.D, peak.getGear());
        assertEquals(3300, peak.getEngRpm());      // 800 + 100 * 25

        // 再走 50 帧：回到 0 km/h（P 档、怠速 800）
        for (int i = 0; i < 50; i++) {
            scheduler.tick();
        }
        VehicleState valley = listener.states.get(99);
        assertEquals(0, valley.getVehSpeedKph());
        assertEquals(Gear.P, valley.getGear());
        assertEquals(800, valley.getEngRpm());
    }

    @Test
    public void invalidWindow_emitsInvalidSpeedFrames() {
        ControllableScheduler scheduler = new ControllableScheduler();
        MockVehicleDataSource source = new MockVehicleDataSource(scheduler.scheduler());
        RecordingListener listener = new RecordingListener();
        source.start(listener);

        // 第 600 帧进入 600-700 非法数据窗口
        for (int i = 0; i < 600; i++) {
            scheduler.tick();
        }
        VehicleState invalid = listener.states.get(599);
        assertEquals(600L, invalid.getSequence());
        assertEquals(255, invalid.getVehSpeedKph()); // 超出 200 上限
        assertEquals(9999, invalid.getEngRpm());
        assertEquals(DataValidity.INVALID_SPEED, invalid.getValidity());
        assertEquals(DataStatus.INVALID, invalid.getDataStatus());
    }

    @Test
    public void silentWindow_reportsNoData_thenResumesConnected() {
        ControllableScheduler scheduler = new ControllableScheduler();
        MockVehicleDataSource source = new MockVehicleDataSource(scheduler.scheduler());
        RecordingListener listener = new RecordingListener();
        source.start(listener);
        listener.statuses.clear();

        // 推进到第 699 帧（seq=699，仍在非法数据窗口内，无状态变更回调）
        for (int i = 0; i < 699; i++) {
            scheduler.tick();
        }
        int statesBeforeSilence = listener.states.size();
        assertEquals(0, listener.statuses.size());

        // 第 700 帧进入静默窗口：上报 NO_DATA，且不产生状态帧
        scheduler.tick();
        assertEquals(Collections.singletonList(DataSourceStatus.NO_DATA),
                listener.statuses);
        assertEquals(statesBeforeSilence, listener.states.size());

        // 静默窗口（seq 701..999）内不再产生任何回调
        for (int i = 0; i < 299; i++) {
            scheduler.tick();
        }
        assertEquals(statesBeforeSilence, listener.states.size());
        assertEquals(1, listener.statuses.size());

        // seq=1000 恢复正常：上报 CONNECTED 并继续产生帧
        scheduler.tick();
        assertEquals(2, listener.statuses.size());
        assertEquals(DataSourceStatus.CONNECTED,
                listener.statuses.get(listener.statuses.size() - 1));
        assertEquals(statesBeforeSilence + 1, listener.states.size());
        VehicleState resumed = listener.states.get(listener.states.size() - 1);
        assertEquals(1000L, resumed.getSequence());
        assertEquals(DataValidity.VALID, resumed.getValidity());
    }

    @Test
    public void start_whileAlreadyRunning_ignoresSecondListener() {
        ControllableScheduler scheduler = new ControllableScheduler();
        MockVehicleDataSource source = new MockVehicleDataSource(scheduler.scheduler());
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();

        source.start(first);
        source.start(second);
        assertTrue(source.isRunning());
        assertEquals(0, second.states.size());
        assertEquals(0, second.statuses.size());

        scheduler.tick();
        assertEquals(1, first.states.size());
        assertEquals(0, second.states.size());
    }

    @Test
    public void stop_reportsStopped_andClearsRunningState() {
        ControllableScheduler scheduler = new ControllableScheduler();
        MockVehicleDataSource source = new MockVehicleDataSource(scheduler.scheduler());
        RecordingListener listener = new RecordingListener();
        source.start(listener);
        listener.statuses.clear();

        source.stop();

        assertFalse(source.isRunning());
        assertEquals(Collections.singletonList(DataSourceStatus.STOPPED),
                listener.statuses);
    }

    @Test
    public void factory_mockSourceType_createsMockDataSource() {
        VehicleDataSource dataSource = VehicleDataSourceFactory.create(
                null, SourceType.MOCK);
        assertSame(MockVehicleDataSource.class, dataSource.getClass());
        assertFalse(dataSource.isRunning());
    }
}
