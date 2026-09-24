package com.example.carlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** BroadcastLabActivity 的 Instrumentation 测试： 电源广播接收与 onStop 时的反注册。 */
@RunWith(AndroidJUnit4::class)
open class BroadcastLabActivityTest {

    private fun idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    open fun powerBroadcasts_updateStatusText() {
        ActivityScenario.launch<BroadcastLabActivity?>(BroadcastLabActivity::class.java)!!.use {
            scenario ->
            scenario!!.onActivity { activity ->
                assertEquals(
                    "Waiting for power broadcast",
                    text(activity),
                )
            }

            scenario!!.onActivity { activity ->
                dispatchPowerAction(activity, Intent.ACTION_POWER_CONNECTED)
                assertEquals("Power connected", text(activity))
                dispatchPowerAction(activity, Intent.ACTION_POWER_DISCONNECTED)
                assertEquals("Power disconnected", text(activity))
            }
        }
    }

    @Test
    open fun receiver_isUnregisteredAfterStop() {
        ActivityScenario.launch<BroadcastLabActivity?>(BroadcastLabActivity::class.java)!!.use {
            scenario ->
            scenario!!.onActivity { activity -> assertEquals(true, receiverRegistered(activity)) }
            scenario!!.moveToState(Lifecycle.State.CREATED)
            scenario!!.onActivity { activity -> assertEquals(false, receiverRegistered(activity)) }
        }
    }

    private fun dispatchPowerAction(activity: BroadcastLabActivity?, action: String?) {
        try {
            val field = BroadcastLabActivity::class.java.getDeclaredField("powerReceiver")
            field!!.setAccessible(true)
            (field!!.get(activity) as BroadcastReceiver).onReceive(activity, Intent(action))
        } catch (exception: ReflectiveOperationException) {
            throw AssertionError(exception)
        }
    }

    @Test
    open fun sameInstanceReturningToForeground_registersReceiverAgain() {
        ActivityScenario.launch<BroadcastLabActivity?>(BroadcastLabActivity::class.java)!!.use {
            scenario ->
            val original = java.util.concurrent.atomic.AtomicReference<BroadcastLabActivity?>()
            scenario!!.onActivity(
                ActivityScenario.ActivityAction<BroadcastLabActivity?> { original.set(it) }
            )
            scenario!!.moveToState(Lifecycle.State.CREATED)
            scenario!!.moveToState(Lifecycle.State.RESUMED)
            scenario!!.onActivity { activity ->
                org.junit.Assert.assertSame(original.get(), activity)
                org.junit.Assert.assertTrue(
                    "onStart must register after the previous onStop",
                    receiverRegistered(activity),
                )
                dispatchPowerAction(activity, Intent.ACTION_POWER_CONNECTED)
                assertEquals("Power connected", text(activity))
            }
        }
    }

    private fun receiverRegistered(activity: BroadcastLabActivity?): Boolean {
        try {
            val field = BroadcastLabActivity::class.java.getDeclaredField("receiverRegistered")
            field!!.setAccessible(true)
            return field!!.getBoolean(activity)
        } catch (exception: ReflectiveOperationException) {
            throw AssertionError(exception)
        }
    }

    private fun text(activity: BroadcastLabActivity?): String? =
        (activity!!.findViewById<View?>(R.id.broadcastStatusText) as TextView).getText().toString()
}
