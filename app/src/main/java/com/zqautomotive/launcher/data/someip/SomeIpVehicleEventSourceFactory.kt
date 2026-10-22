package com.zqautomotive.launcher.data.someip

import android.content.Context
import com.zqautomotive.launcher.data.VehicleDataSource
import com.zqautomotive.launcher.data.VehicleEventSourceFactory
import com.zqautomotive.vehicle.someip.adapter.SomeipConnectionMonitor
import com.zqautomotive.vehicle.someip.android.VSomeIpNativeTransport

/** The sole production composition point for the vehicle SOME/IP source and transport. */
object SomeIpVehicleEventSourceFactory : VehicleEventSourceFactory {
    override fun create(context: Context, monitor: SomeipConnectionMonitor): VehicleDataSource =
        VSomeIpDataSource(VSomeIpNativeTransport(context.applicationContext), monitor)
}
