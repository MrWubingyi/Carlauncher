package com.zqautomotive.launcher.testing

import com.zqautomotive.vehicle.control.*
import com.zqautomotive.launcher.data.DataSourceStatus
import com.zqautomotive.launcher.model.DataValidity
import com.zqautomotive.launcher.model.VehicleState
import com.zqautomotive.launcher.service.VehicleSendService
import com.zqautomotive.vehicle.someip.adapter.SomeipConnectionMonitor

/**
 * 测试替身：[VehicleSendService] 的子类。
 *
 * 仅为 Repository / ViewModel / Compose 页面提供可控的服务快照读取， 不触发 onCreate、前台通知、TCP 或数据源等真实副作用。
 */
class StubVehicleSendService : VehicleSendService() {

    var controls = VehicleControlSnapshot()
    val commands = mutableListOf<VehicleAction>()
    override fun controlSnapshot(): VehicleControlSnapshot = controls
    override fun requestControl(action: VehicleAction) { commands += action }

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
