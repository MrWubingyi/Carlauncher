package com.example.carlauncher.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * VehicleSendService 的轻量 Instrumentation 测试：仅覆盖构造后的默认状态、
 * Binder 与状态监听器注册/通知，不触发 onCreate（无前台/TCP/数据源副作用）。
 */
@RunWith(AndroidJUnit4.class)
public class VehicleSendServiceTest {

    private VehicleSendService newServiceInstance() {
        // 直接构造实例而不调用 onCreate，避免启动前台通知与真实 TCP/数据源。
        return new VehicleSendService();
    }

    @Test
    public void newInstance_hasStoppedDefaults() {
        VehicleSendService service = newServiceInstance();

        assertEquals(TcpConnectionState.DISCONNECTED, service.getTcpState());
        assertEquals(DataSourceStatus.STOPPED, service.getSourceStatus());
        assertNull(service.getLatestVehicleState());
        assertFalse(service.isSending());
        assertFalse(service.isConnecting());
        assertFalse(service.isTcpConnected());
    }

    @Test
    public void getDataValidity_withoutVehicleState_returnsIncomplete() {
        VehicleSendService service = newServiceInstance();
        assertEquals(DataValidity.INCOMPLETE, service.getDataValidity());
    }

    @Test
    public void setStateListener_notifiesListenerOnMainLooper() {
        VehicleSendService service = newServiceInstance();
        AtomicInteger notifications = new AtomicInteger();

        service.setStateListener(notifications::incrementAndGet);
        // notifyStateChanged 通过主线程 Handler 投递
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals(1, notifications.get());
    }

    @Test
    public void clearStateListener_stopsNotifications() {
        VehicleSendService service = newServiceInstance();
        AtomicInteger notifications = new AtomicInteger();

        VehicleSendService.StateListener listener = notifications::incrementAndGet;
        service.setStateListener(listener);
        service.clearStateListener(listener);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals(0, notifications.get());
    }

    @Test
    public void onBind_returnsLocalBinder_forSameService() {
        VehicleSendService service = newServiceInstance();
        IBinder binder = service.onBind(new Intent("com.example.carlauncher.TEST_BIND"));

        assertTrue(binder instanceof VehicleSendService.LocalBinder);
        assertSame(service,
                ((VehicleSendService.LocalBinder) binder).getService());
    }

    @Test
    public void onStartCommand_returnsStartSticky() {
        VehicleSendService service = newServiceInstance();
        assertEquals(Service.START_STICKY,
                service.onStartCommand(null, 0, 1));
    }
}



