package com.example.carlauncher.someip

import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.Gear
import com.example.carlauncher.model.TurnSignal
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.model.WarningState
import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

open class VSomeIpDataSourceTest {

    private var fakeTransport: FakeNativeVehicleTransport? = null
    private var connectionMonitor: SomeipConnectionMonitor? = null
    private var dataSource: VSomeIpDataSource? = null
    private var listener: TestVehicleDataSourceListener? = null

    @Before
    open fun setUp() {
        fakeTransport = FakeNativeVehicleTransport()
        connectionMonitor = SomeipConnectionMonitor()
        dataSource = VSomeIpDataSource(fakeTransport, connectionMonitor)
        listener = TestVehicleDataSourceListener()
    }

    @Test
    open fun start_subscribesToTransportAndNotifiesConnecting() {
        dataSource!!.start(listener)

        assertTrue(dataSource!!.isRunning)
        assertEquals(DataSourceStatus.CONNECTING, dataSource!!.status)
        assertTrue(listener!!.sourceStatuses.contains(DataSourceStatus.CONNECTING))
    }

    @Test
    open fun availabilityChanged_updatesStatusAndMonitor() {
        dataSource!!.start(listener)

        fakeTransport!!.notifyAvailability(true)
        assertEquals(DataSourceStatus.CONNECTING, dataSource!!.status)

        fakeTransport!!.notifyAvailability(false)
        assertEquals(DataSourceStatus.DISCONNECTED, dataSource!!.status)
        assertNull(dataSource!!.latestState)
    }

    @Test
    open fun vehicleState_updatesLatestStateAndStatusConnected() {
        dataSource!!.start(listener)
        fakeTransport!!.notifyAvailability(true)

        val sampleState = createSampleVehicleState(1, 1000L, 60, DataStatus.NORMAL)
        fakeTransport!!.notifyVehicleState(sampleState)

        assertEquals(sampleState, dataSource!!.latestState)
        assertEquals(DataSourceStatus.CONNECTED, dataSource!!.status)
        assertEquals(1, listener!!.receivedStates.size.toLong())
        assertEquals(sampleState, listener!!.receivedStates.get(0))
    }

    @Test
    open fun vehicleState_withNoData_updatesStatusNoData() {
        dataSource!!.start(listener)
        fakeTransport!!.notifyAvailability(true)

        val sampleState = createSampleVehicleState(2, 2000L, 0, DataStatus.NO_DATA)
        fakeTransport!!.notifyVehicleState(sampleState)

        assertEquals(DataSourceStatus.NO_DATA, dataSource!!.status)
    }

    @Test
    open fun error_updatesStatusErrorAndNotifiesListener() {
        dataSource!!.start(listener)

        val testError = RuntimeException("Test protocol error")
        fakeTransport!!.notifyError(testError)

        assertEquals(DataSourceStatus.ERROR, dataSource!!.status)
        assertEquals(1, listener!!.receivedErrors.size.toLong())
        assertEquals(testError, listener!!.receivedErrors.get(0))
    }

    @Test
    open fun stop_unsubscribesAndResetsStatus() {
        dataSource!!.start(listener)
        fakeTransport!!.notifyAvailability(true)

        dataSource!!.stop()

        assertFalse(dataSource!!.isRunning)
        assertEquals(DataSourceStatus.STOPPED, dataSource!!.status)
        assertNull(dataSource!!.latestState)
    }

    // ---- Helper Classes ----

    private fun createSampleVehicleState(
        seq: Long,
        timestamp: Long,
        speed: Int,
        status: DataStatus?,
    ): VehicleState? =
        VehicleState.Builder()
            .setVersion(1)
            .setSequence(seq)
            .setTimestampMs(timestamp)
            .setVehSpeedKph(speed)
            .setEngRpm(2000)
            .setSoc(80)
            .setGear(Gear.D)
            .setTurnSignal(TurnSignal.NONE)
            .setWarning(WarningState.NONE)
            .setValidity(DataValidity.VALID)
            .setDataStatus(status)
            .setParkingBrake(false)
            .setDoorLock(true)
            .setBeltWarning(false)
            .setHeadlightsState(0)
            .setHighBeamLightsState(0)
            .setEngineCoolantTemp(90.0f)
            .setEvBatteryLevel(80.0f)
            .build()

    private open class FakeNativeVehicleTransport : NativeVehicleTransport {
        private var listener: NativeVehicleTransport.Listener? = null
        final override var isAvailable: Boolean = false
            private set

        private var started: Boolean = false

        override fun start(): Boolean {
            started = true
            return true
        }

        override fun stop() {
            started = false
            isAvailable = false
        }

        override fun setListener(listener: NativeVehicleTransport.Listener?) {
            this.listener = listener
        }

        internal open fun notifyAvailability(available: Boolean) {
            this.isAvailable = available
            if (listener != null) {
                listener!!.onAvailabilityChanged(available)
            }
        }

        internal open fun notifyVehicleState(state: VehicleState?) {
            if (listener != null) {
                listener!!.onVehicleState(state)
            }
        }

        internal open fun notifyError(throwable: Throwable?) {
            if (listener != null) {
                listener!!.onError(throwable)
            }
        }
    }

    private open class TestVehicleDataSourceListener : VehicleDataSource.Listener {
        internal val receivedStates: MutableList<VehicleState?> = ArrayList<VehicleState?>()
        internal val sourceStatuses: MutableList<DataSourceStatus?> = ArrayList<DataSourceStatus?>()
        internal val receivedErrors: MutableList<Exception?> = ArrayList<Exception?>()

        override fun onStateChanged(state: VehicleState?) {
            receivedStates.add(state)
        }

        override fun onSourceStatusChanged(status: DataSourceStatus?) {
            sourceStatuses.add(status)
        }

        override fun onError(exception: Exception?) {
            receivedErrors.add(exception)
        }
    }
}
