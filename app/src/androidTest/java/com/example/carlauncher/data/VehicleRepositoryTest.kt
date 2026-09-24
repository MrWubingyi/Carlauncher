package com.example.carlauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.testing.StubVehicleSendService
import com.example.carlauncher.ui.CockpitConnectionState
import com.example.carlauncher.ui.CockpitUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** VehicleRepository 在“服务快照 → CockpitUiState”上的状态流测试（Instrumentation）。 */
@RunWith(AndroidJUnit4::class)
open class VehicleRepositoryTest {

    @Test
    open fun initial_returnsUnboundDisconnectedState() {
        val repository = VehicleRepository()
        val state = repository.getUiState()!!.getValue()

        assertFalse(state!!.isServiceBound)
        assertNull(state!!.vehicleState)
        assertEquals(DataSourceStatus.STOPPED, state!!.dataSourceStatus)
        assertEquals(CockpitConnectionState.DISCONNECTED, state!!.state)
    }

    @Test
    open fun refresh_withoutService_keepsInitialState() {
        val repository = VehicleRepository()
        onMain(Runnable { repository.refresh() })

        val state = repository.getUiState()!!.getValue()
        assertFalse(state!!.isServiceBound)
        assertEquals(CockpitConnectionState.DISCONNECTED, state!!.state)
    }

    @Test
    open fun attachService_exposesServiceSnapshotAsUiState() {
        val repository = VehicleRepository()
        val vehicleState =
            VehicleState.Builder()
                .setSequence(7L)
                .setVehSpeedKph(66)
                .setValidity(DataValidity.VALID)
                .build()

        onMain {
            repository.attachService(
                StubVehicleSendService()
                    .withLatestState(vehicleState)!!
                    .withSourceStatus(DataSourceStatus.CONNECTED)!!
                    .withSomeipAvailable(true, 0)!!
                    .withSomeipResponse(true, 0, 1)!!
                    .withValidity(DataValidity.VALID)
            )
        }

        val state = repository.getUiState()!!.getValue()
        assertTrue(state!!.isServiceBound)
        assertEquals(vehicleState, state!!.vehicleState)
        assertEquals(DataSourceStatus.CONNECTED, state!!.dataSourceStatus)
        assertEquals(CockpitConnectionState.ONLINE, state!!.state)
    }

    @Test
    open fun attachService_withOnlineButInvalidValidity_mapsToInvalidData() {
        val repository = VehicleRepository()

        onMain {
            repository.attachService(
                StubVehicleSendService()
                    .withLatestState(VehicleState.Builder().build())!!
                    .withSomeipAvailable(true, 0)!!
                    .withSomeipResponse(true, 0, 1)!!
                    .withValidity(DataValidity.INVALID_SPEED)
            )
        }

        assertEquals(
            CockpitConnectionState.INVALID_DATA,
            repository.getUiState()!!.getValue()!!.state,
        )
    }

    @Test
    open fun refresh_afterAttach_reflectsUpdatedSnapshot() {
        val repository = VehicleRepository()
        val service =
            StubVehicleSendService()
                .withLatestState(VehicleState.Builder().build())!!
                .withSomeipAvailable(true, 0)!!
                .withSomeipResponse(true, 0, 1)!!
                .withValidity(DataValidity.VALID)
        onMain { repository.attachService(service) }
        assertEquals(
            CockpitConnectionState.ONLINE,
            repository.getUiState()!!.getValue()!!.state,
        )

        // 服务状态变化后由外部触发刷新
        service!!.withSomeipAvailable(false, 100)!!.withSomeipAvailable(true, 200)
        onMain(Runnable { repository.refresh() })

        val refreshed = repository.getUiState()!!.getValue()
        assertEquals(CockpitConnectionState.RECOVERING, refreshed!!.state)
    }

    @Test
    open fun detachService_returnsToInitialState() {
        val repository = VehicleRepository()
        onMain {
            repository.attachService(
                StubVehicleSendService()
                    .withLatestState(VehicleState.Builder().build())!!
                    .withSomeipAvailable(true, 0)!!
                    .withSomeipResponse(true, 0, 1)
            )
        }

        onMain(Runnable { repository.detachService() })

        val state = repository.getUiState()!!.getValue()
        assertFalse(state!!.isServiceBound)
        assertNull(state!!.vehicleState)
        assertEquals(DataSourceStatus.STOPPED, state!!.dataSourceStatus)
        assertEquals(CockpitConnectionState.DISCONNECTED, state!!.state)
    }

    private fun onMain(action: Runnable?) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    @Test
    open fun someipWaitingErrorAndTimeoutRecoverOnResponse() {
        val repository = VehicleRepository()
        val service =
            StubVehicleSendService().withValidity(DataValidity.VALID)!!.withSomeipAvailable(true, 0)
        onMain { repository.attachService(service) }
        assertEquals(
            "SOME/IP WAITING EVENT",
            repository.getUiState()!!.getValue()!!.connectionLabel,
        )

        service!!.withSomeipResponse(false, 1, 100)
        onMain(Runnable { repository.refresh() })
        assertEquals(
            "SOME/IP RESPONSE_ERROR",
            repository.getUiState()!!.getValue()!!.connectionLabel,
        )

        service!!.checkSomeipTimeout(3100)
        onMain(Runnable { repository.refresh() })
        assertEquals(
            "SOME/IP EVENT TIMEOUT",
            repository.getUiState()!!.getValue()!!.connectionLabel,
        )
        assertTrue(repository.getUiState()!!.getValue()!!.isStopAction())

        service!!.withSomeipResponse(true, 0, 3200)
        onMain(Runnable { repository.refresh() })
        assertEquals("SOME/IP ONLINE", repository.getUiState()!!.getValue()!!.connectionLabel)
        service!!.withSomeipAvailable(false, 3300)
        onMain(Runnable { repository.refresh() })
        assertEquals("SOME/IP UNAVAILABLE", repository.getUiState()!!.getValue()!!.connectionLabel)
    }
}
