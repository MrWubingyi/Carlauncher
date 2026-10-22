package com.zqautomotive.launcher.ui.compose

import android.content.Context
import android.view.View
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.ViewModelStore
import com.zqautomotive.launcher.ui.home.host.LauncherConnection
import com.zqautomotive.launcher.ui.home.map.MapTask
import com.zqautomotive.launcher.ui.home.map.MapTaskFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarLauncherHostTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val model = CarLauncherViewModel()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        store.put("launcher", model)
    }

    @After fun cleanup() {
        store.clear()
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    @Test fun repeatedStartHasOneTickerAndStopPausesPlayback() = runTest(dispatcher) {
        val connection = Connection()
        model.startHost(connection)
        model.startHost(connection)
        model.togglePlayPause()
        val initial = model.uiState.value.positionSeconds
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(initial + 1, model.uiState.value.positionSeconds)
        assertEquals(1, connection.starts)
        model.onHostStop()
        model.onHostStop()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(initial + 1, model.uiState.value.positionSeconds)
        assertEquals(1, connection.stops)
        assertFalse(model.hostStarted.value)
        model.startHost(connection)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(initial + 2, model.uiState.value.positionSeconds)
        model.onHostStop()
    }

    @Test fun mapTaskSurvivesHostRecreationAndClosesWithViewModel() {
        var creations = 0
        val task = Task()
        model.configureMapTask(MapTaskFactory { creations++; task })
        val connection = Connection()
        model.startHost(connection)
        model.onHostStop()
        assertEquals(0, task.closes)
        model.startHost(connection)
        assertSame(task, model.mapTask.value)
        assertEquals(1, creations)
        assertEquals(2, task.starts)
        store.clear()
        assertEquals(2, task.stops)
        assertEquals(1, task.closes)
        assertNull(model.mapTask.value)
        assertFalse(model.hostStarted.value)
    }

    @Test fun stoppedHostRejectsConnectionActionAndStartedHostReturnsFailure() {
        val connection = Connection()
        assertNotNull(model.toggleConnection())
        model.startHost(connection)
        connection.failure = "bind failed"
        assertEquals("bind failed", model.toggleConnection())
        assertEquals(1, connection.toggles)
        assertFalse(model.uiState.value.isDemo)
        model.onHostStop()
        assertNotNull(model.toggleConnection())
        assertEquals(1, connection.toggles)
    }

    @Test fun absentTaskUsesDemoAndCannotBeReconfiguredAfterStart() {
        model.startHost(Connection())
        assertNull(model.mapTask.value)
        assertThrows(IllegalStateException::class.java) {
            model.configureMapTask(MapTaskFactory { Task() })
        }
    }

    private class Connection : LauncherConnection {
        var starts = 0
        var stops = 0
        var toggles = 0
        var failure: String? = null
        override fun onStart() { starts++ }
        override fun onStop() { stops++ }
        override fun toggle(): String? { toggles++; return failure }
    }

    private class Task : MapTask {
        var starts = 0
        var stops = 0
        var closes = 0
        override fun createView(context: Context): View = error("Views require an Android host")
        override fun releaseView(view: View) = error("No view created in lifecycle test")
        override fun onStart() { starts++ }
        override fun onStop() { stops++ }
        override fun close() { closes++ }
    }
}
