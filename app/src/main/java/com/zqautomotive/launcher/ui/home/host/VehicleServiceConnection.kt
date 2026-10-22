package com.zqautomotive.launcher.ui.home.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.zqautomotive.launcher.service.VehicleSendService

/** Uses application context only. Each binding has its own callback identity. */
class VehicleServiceConnection(
    context: Context,
    private val onConnected: (VehicleSendService) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onChanged: () -> Unit,
) : LauncherConnection {
    private val context = context.applicationContext
    private val intent = Intent(this.context, VehicleSendService::class.java)
    private var registration: ServiceConnection? = null
    private var service: VehicleSendService? = null
    private val listener = VehicleSendService.StateListener { onChanged() }

    override fun onStart() {
        if (registration == null) bind(0)
    }

    override fun onStop() = releaseBinding()

    override fun toggle(): String? {
        if (service != null) {
            releaseBinding()
            context.stopService(intent)
            return null
        }
        // A pending flags=0 registration does not mean the service is running.
        releaseBinding()
        return try {
            ContextCompat.startForegroundService(context, intent)
            if (bind(Context.BIND_AUTO_CREATE)) null else {
                context.stopService(intent)
                "无法绑定车辆接收服务"
            }
        } catch (error: RuntimeException) {
            releaseBinding()
            context.stopService(intent)
            Log.e(TAG, "Unable to start vehicle service", error)
            "无法启动接收服务：${error.localizedMessage}"
        }
    }

    private fun bind(flags: Int): Boolean {
        val callback = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (registration !== this) return
                val connected = (binder as? VehicleSendService.LocalBinder)?.service
                if (connected == null) {
                    releaseBinding()
                    return
                }
                service = connected
                onConnected(connected)
                connected.setStateListener(listener)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (registration !== this) return
                service?.clearStateListener(listener)
                service = null
                onDisconnected()
            }

            override fun onBindingDied(name: ComponentName?) {
                if (registration === this) releaseBinding()
            }

            override fun onNullBinding(name: ComponentName?) {
                if (registration === this) releaseBinding()
            }
        }
        registration = callback
        return try {
            if (context.bindService(intent, callback, flags)) true else {
                releaseBinding()
                false
            }
        } catch (error: RuntimeException) {
            releaseBinding()
            Log.e(TAG, "Unable to bind vehicle service", error)
            false
        }
    }

    private fun releaseBinding() {
        val callback = registration
        registration = null
        service?.clearStateListener(listener)
        service = null
        if (callback != null) {
            try {
                context.unbindService(callback)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Binding was not registered", error)
            }
        }
        onDisconnected()
    }

    private companion object {
        const val TAG = "LauncherConnection"
    }
}
