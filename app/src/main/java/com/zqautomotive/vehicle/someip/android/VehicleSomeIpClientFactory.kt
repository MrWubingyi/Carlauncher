package com.zqautomotive.vehicle.someip.android

import android.content.Context
import com.example.someip.SomeIpClient
import com.example.someip.VSomeIp
import java.io.File

/** Vehicle endpoint selection and local configuration policy belong to the consuming app. */
internal object VehicleSomeIpClientFactory {
    fun create(context: Context?): SomeIpClient {
        val application = requireNotNull(context) { "A Context is required to start SOME/IP" }
            .applicationContext
        return create(application, SomeIpConfigFile(application).prepare())
    }

    fun create(context: Context, configFile: File): SomeIpClient = VSomeIp.create(context, configFile)
}
