package com.example.carlauncher.ui

import androidx.lifecycle.LiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.testing.StubVehicleSendService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** CockpitViewModel 委托 Repository 的快照流（Instrumentation）。 */
@RunWith(AndroidJUnit4::class)
open class CockpitViewModelTest {

    @Test
    open fun getUiState_neverNull_startsWithInitialState() {
        val viewModel = CockpitViewModel()

        val liveData = viewModel.uiState
        assertNotNull(liveData)
        val state = liveData!!.getValue()
        assertNotNull(state)
        assertFalse(state!!.isServiceBound)
        assertEquals(CockpitConnectionState.DISCONNECTED, state!!.state)
    }

    @Test
    open fun attachService_emitsBoundState() {
        val viewModel = CockpitViewModel()
        val vehicleState =
            VehicleState.Builder().setSequence(3L).setValidity(DataValidity.VALID).build()

        onMain {
            viewModel.attachService(
                StubVehicleSendService()
                    .withLatestState(vehicleState)!!
                    .withSourceStatus(DataSourceStatus.CONNECTED)!!
                    .withSomeipAvailable(true, 0)!!
                    .withSomeipResponse(true, 0, 1)!!
                    .withValidity(DataValidity.VALID)
            )
        }

        val state = viewModel.uiState!!.getValue()
        assertTrue(state!!.isServiceBound)
        assertEquals(vehicleState, state!!.vehicleState)
        assertEquals(CockpitConnectionState.ONLINE, state!!.state)
    }

    @Test
    open fun detachService_returnsToInitialState() {
        val viewModel = CockpitViewModel()
        onMain {
            viewModel.attachService(
                StubVehicleSendService().withLatestState(VehicleState.Builder().build())
            )
        }

        onMain(Runnable { viewModel.detachService() })

        val state = viewModel.uiState!!.getValue()
        assertFalse(state!!.isServiceBound)
        assertNull(state!!.vehicleState)
        assertEquals(DataSourceStatus.STOPPED, state!!.dataSourceStatus)
    }

    @Test
    open fun refresh_withoutAttachedService_staysInitial() {
        val viewModel = CockpitViewModel()
        onMain(Runnable { viewModel.refresh() })

        val state = viewModel.uiState!!.getValue()
        assertFalse(state!!.isServiceBound)
        assertEquals(CockpitConnectionState.DISCONNECTED, state!!.state)
    }

    private fun onMain(action: Runnable?) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }
}
