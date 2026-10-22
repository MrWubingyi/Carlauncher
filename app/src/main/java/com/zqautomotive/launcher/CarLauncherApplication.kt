package com.zqautomotive.launcher

import android.app.Application
import com.zqautomotive.launcher.data.VehicleEventSourceFactory
import com.zqautomotive.launcher.data.VehicleServiceDependencies
import com.zqautomotive.launcher.data.someip.SomeIpVehicleEventSourceFactory

open class CarLauncherApplication : Application(), VehicleServiceDependencies {
    override val vehicleEventSourceFactory: VehicleEventSourceFactory = SomeIpVehicleEventSourceFactory

    override fun onCreate() {
        super.onCreate()
        ThemePreferences.applySavedTheme(this)
    }
}
