package com.zqautomotive.launcher.data

import android.content.Context
import com.zqautomotive.vehicle.someip.adapter.SomeipConnectionMonitor

/** Creates an unstarted source. The caller owns start/stop on its lifecycle executor. */
fun interface VehicleEventSourceFactory {
    fun create(context: Context, monitor: SomeipConnectionMonitor): VehicleDataSource
}

/** Application composition contract consumed by the Android-created Service. */
interface VehicleServiceDependencies {
    val vehicleEventSourceFactory: VehicleEventSourceFactory
}
