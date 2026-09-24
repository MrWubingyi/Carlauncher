package com.example.carlauncher.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.model.DataValidity
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * VehicleSendService 的轻量 Instrumentation 测试：仅覆盖构造后的默认状态、 Binder 与状态监听器注册/通知，不触发
 * onCreate（无前台/TCP/数据源副作用）。
 */
@RunWith(AndroidJUnit4::class)
open class VehicleSendServiceTest {

    private fun newServiceInstance(): VehicleSendService? {
        // 直接构造实例而不调用 onCreate，避免启动前台通知与真实 TCP/数据源。
        return VehicleSendService()
    }

    @Test
    open fun newInstance_hasStoppedDefaults() {
        val service = newServiceInstance()
        assertEquals(DataSourceStatus.STOPPED, service!!.getSourceStatus())
        assertNull(service!!.getLatestVehicleState())
    }

    @Test
    open fun getDataValidity_withoutVehicleState_returnsIncomplete() {
        val service = newServiceInstance()
        assertEquals(DataValidity.INCOMPLETE, service!!.dataValidity)
    }

    @Test
    open fun setStateListener_notifiesListenerOnMainLooper() {
        val service = newServiceInstance()
        val notifications = AtomicInteger()

        service!!.setStateListener(
            VehicleSendService.StateListener { notifications.incrementAndGet() }
        )
        // notifyStateChanged 通过主线程 Handler 投递
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(1, notifications.get().toLong())
    }

    @Test
    open fun clearStateListener_stopsNotifications() {
        val service = newServiceInstance()
        val notifications = AtomicInteger()

        val listener = VehicleSendService.StateListener { notifications.incrementAndGet() }
        service!!.setStateListener(listener)
        service!!.clearStateListener(listener)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(0, notifications.get().toLong())
    }

    @Test
    open fun onBind_returnsLocalBinder_forSameService() {
        val service = newServiceInstance()
        val binder = service!!.onBind(Intent("com.example.carlauncher.TEST_BIND"))

        assertTrue(binder is VehicleSendService.LocalBinder)
        assertSame(
            service,
            (binder as VehicleSendService.LocalBinder).service,
        )
    }

    @Test
    open fun onStartCommand_returnsStartSticky() {
        val service = newServiceInstance()
        assertEquals(
            Service.START_STICKY.toLong(),
            service!!.onStartCommand(null, 0, 1).toLong(),
        )
    }
}
