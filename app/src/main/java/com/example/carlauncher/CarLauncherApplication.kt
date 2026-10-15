package com.example.carlauncher

import android.app.Application
import com.example.carlauncher.data.VehicleEventSourceFactory
import com.example.carlauncher.data.VehicleServiceDependencies
import com.example.carlauncher.data.someip.SomeIpVehicleEventSourceFactory

open class CarLauncherApplication : Application(), VehicleServiceDependencies {
    override val vehicleEventSourceFactory: VehicleEventSourceFactory = SomeIpVehicleEventSourceFactory

    override fun onCreate() {
        super.onCreate()
        ThemePreferences.applySavedTheme(this)
    }
}
