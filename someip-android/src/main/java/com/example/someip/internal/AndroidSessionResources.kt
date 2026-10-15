package com.example.someip.internal

import android.content.Context
import android.net.wifi.WifiManager
import com.example.someip.SomeIpClientConfig
import com.example.someip.internal.network.AndroidNetworkMonitor
import java.io.File
import java.net.InetAddress
import org.json.JSONObject

/** Owns platform resources only; configuration copying and local-IP policy belong to the caller. */
internal class AndroidSessionResources(private val context: Context) : SessionCoordinator.Resources {
    override fun acquire(config: SomeIpClientConfig): AutoCloseable {
        val json = JSONObject(File(config.configPath).readText(Charsets.UTF_8))
        val discovery = json.optJSONObject("service-discovery")
        val enabled = discovery?.optBoolean("enable", true) ?: true
        val multicast = if (enabled) {
            InetAddress.getByName(discovery?.optString("multicast", DEFAULT_MULTICAST) ?: DEFAULT_MULTICAST)
        } else null
        val monitor = AndroidNetworkMonitor(
            context,
            json.getString("unicast"),
            multicast,
            AndroidNetworkMonitor.Sink(NativeBridge::nativeNetworkState),
        )
        var lease: WifiManager.MulticastLock? = null
        var acquired = false
        try {
            if (enabled) {
                lease = context.getSystemService(WifiManager::class.java)
                    ?.createMulticastLock("someip-service-discovery")
                lease?.setReferenceCounted(false)
                lease?.acquire()
            }
            monitor.start()
            acquired = true
            return AutoCloseable {
                try {
                    monitor.close()
                } finally {
                    lease?.let { if (it.isHeld) it.release() }
                }
            }
        } finally {
            if (!acquired) {
                try {
                    monitor.close()
                } finally {
                    lease?.let { if (it.isHeld) it.release() }
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_MULTICAST = "224.244.224.245"
    }
}
