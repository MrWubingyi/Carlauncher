package com.example.carlauncher.data.someip

import android.content.Context
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.data.VehicleEventSourceFactory
import com.example.vehicle.someip.adapter.SomeipConnectionMonitor
import com.example.vehicle.someip.android.VSomeIpNativeTransport

/** The sole production composition point for the vehicle SOME/IP source and transport. */
object SomeIpVehicleEventSourceFactory : VehicleEventSourceFactory {
    override fun create(context: Context, monitor: SomeipConnectionMonitor): VehicleDataSource =
        VSomeIpDataSource(VSomeIpNativeTransport(context.applicationContext), monitor)
}
