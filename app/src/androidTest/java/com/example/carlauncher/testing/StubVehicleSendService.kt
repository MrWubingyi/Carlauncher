package com.example.carlauncher.testing

import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.service.VehicleSendService
import com.example.carlauncher.someip.SomeipConnectionMonitor

/**
 * 测试替身：[VehicleSendService] 的子类。
 *
 * 仅为 Repository / ViewModel / MainActivity 提供可控的服务快照读取， 不触发 onCreate、前台通知、TCP 或数据源等真实副作用。
 */
class StubVehicleSendService : VehicleSendService() {

    private var latestState: VehicleState? = null
    private var sourceStatus: DataSourceStatus? = DataSourceStatus.CONNECTED
    override var dataValidity: DataValidity? = DataValidity.VALID
        private set

    private val someip: SomeipConnectionMonitor = SomeipConnectionMonitor()

    override val someipStatus: SomeipConnectionMonitor.Snapshot?
        get() {
            return someip.snapshot()
        }

    fun withSomeipResponse(ok: Boolean, code: Int, now: Long): StubVehicleSendService {
        someip.onResponse(ok, code, now)
        return this
    }

    fun withSomeipAvailable(available: Boolean, now: Long): StubVehicleSendService {
        if (someip.snapshot().state == SomeipConnectionMonitor.State.STOPPED) someip.start()
        someip.onAvailability(available, now)
        return this
    }

    fun checkSomeipTimeout(now: Long): StubVehicleSendService {
        someip.checkTimeout(now)
        return this
    }

    fun withLatestState(state: VehicleState?): StubVehicleSendService {
        this.latestState = state
        return this
    }

    fun withSourceStatus(status: DataSourceStatus?): StubVehicleSendService {
        this.sourceStatus = status
        return this
    }

    fun withValidity(validity: DataValidity?): StubVehicleSendService {
        this.dataValidity = validity
        return this
    }

    override fun getLatestVehicleState(): VehicleState? = latestState

    override fun getSourceStatus(): DataSourceStatus? = sourceStatus
}
