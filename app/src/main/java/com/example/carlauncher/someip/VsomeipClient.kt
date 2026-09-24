package com.example.carlauncher.someip

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * W37 WP1 第三步草稿 · vSomeIP Client JNI 桥。
 *
 * Android subscribes to Ubuntu Service 0x1111/0x2222 Event 0x8001 (full JSON state). The legacy
 * Method API remains available only for isolated protocol probes.
 *
 * The foreground Service owns start/stop. Event callbacks are dispatched on the main thread.
 *
 * 接入前：config json 需从 assets 拷到 filesDir 后传入 start()；插件 .so 随 APK 打包。
 */
class VsomeipClient private constructor() {

    interface Listener {
        fun onRegistered() {}

        fun onAvailable(available: Boolean)

        fun onResponse(ok: Boolean, returnCode: Int)

        fun onStopped() {}

        fun onVehicleEvent(payload: ByteArray?) {}
    }

    companion object {

        private const val TAG: String = "VSOMEIP_JNI"

        init {
            // CMake links the monolithic core containing configuration and service discovery.
            loadQuietly("vsomeip3")
            System.loadLibrary("carlauncher")
            Log.i(TAG, "System.loadLibrary ok")
        }

        @JvmStatic
        private fun loadQuietly(name: String) {
            try {
                System.loadLibrary(name)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "loadLibrary(" + name + ") failed", e)
            }
        }

        // ---- native 回调 kind（与 carlauncher.cpp 的 JavaEventKind 对齐）----
        private const val EVT_REGISTERED: Int = 0
        private const val EVT_AVAILABLE: Int = 1
        private const val EVT_UNAVAILABLE: Int = 2
        private const val EVT_RESPONSE_OK: Int = 3
        private const val EVT_RESPONSE_ERROR: Int = 4
        private const val EVT_STOPPED: Int = 5

        private val UI: Handler = Handler(Looper.getMainLooper())
        @Volatile private var sListener: Listener? = null
        private var networkMonitor: AndroidNetworkMonitor? = null
        private var multicastLock: WifiManager.MulticastLock? = null

        // ---- JNI ----
        @JvmStatic external fun nativeStart(configPath: String?): Boolean

        @JvmStatic external fun nativeStop()

        /* True means submitted to vSomeIP, not delivered or acknowledged by the peer. */
        @JvmStatic external fun nativeSendState(payload: ByteArray?): Boolean

        @JvmStatic external fun nativeIsAvailable(): Boolean

        @JvmStatic
        external fun nativeNetworkState(
            address: String?,
            iface: String?,
            available: Boolean,
            multicastRoute: Boolean,
        )

        // ---- 高层入口 ----
        /* Service 绑定后调用；configPath = filesDir 下的 vsomeip-client.json 绝对路径。 */
        @JvmStatic @Synchronized fun start(configPath: String?): Boolean = nativeStart(configPath)

        /* Starts a platform observer before native routing. Repeated starts retain one observer. */
        @Synchronized
        @Throws(Exception::class)
        @JvmStatic
        fun start(context: Context?, configPath: String?): Boolean {
            if (networkMonitor != null) return nativeStart(configPath)
            val bytes = ByteArrayOutputStream()
            FileInputStream(configPath).use { `in` ->
                val buffer = ByteArray(4096)
                var count = `in`.read(buffer)
                while (count != -1) {
                    bytes.write(buffer, 0, count)
                    count = `in`.read(buffer)
                }
            }
            val config = JSONObject(String(bytes.toByteArray(), StandardCharsets.UTF_8))
            val sd = config.optJSONObject("service-discovery")
            val discoveryEnabled = sd == null || sd!!.optBoolean("enable", true)
            val multicast =
                if (discoveryEnabled)
                    InetAddress.getByName(
                        if (sd == null) "224.244.224.245"
                        else sd!!.optString("multicast", "224.244.224.245")
                    )
                else null
            val monitor =
                AndroidNetworkMonitor(
                    context!!.getApplicationContext(),
                    config.getString("unicast"),
                    multicast,
                    AndroidNetworkMonitor.Sink { address, iface, available, multicastRoute ->
                        nativeNetworkState(address, iface, available, multicastRoute)
                    },
                )
            var lease: WifiManager.MulticastLock? = null
            var started = false
            try {
                if (discoveryEnabled) {
                    val wifi =
                        context!!
                            .getApplicationContext()
                            .getSystemService<WifiManager>(WifiManager::class.java)
                    if (wifi != null) {
                        lease = wifi!!.createMulticastLock("carlauncher-someip-sd")
                        lease!!.setReferenceCounted(false)
                        lease!!.acquire()
                        Log.i(TAG, "SD multicast lock acquired")
                    }
                }
                monitor.start()
                if (!nativeStart(configPath)) return false
                networkMonitor = monitor
                multicastLock = lease
                started = true
                return true
            } finally {
                if (!started) {
                    try {
                        monitor.close()
                    } finally {
                        if (lease != null && lease!!.isHeld()) lease!!.release()
                    }
                }
            }
        }

        @Synchronized
        @JvmStatic
        fun stop() {
            try {
                if (networkMonitor != null) networkMonitor!!.close()
            } finally {
                networkMonitor = null
                try {
                    nativeStop()
                } finally {
                    if (multicastLock != null && multicastLock!!.isHeld()) multicastLock!!.release()
                    multicastLock = null
                    Log.i(TAG, "SD multicast lock released")
                }
            }
        }

        /* 由 10 Hz DataSource 线程调用：编码 16 B payload 并发送。 */
        @JvmStatic
        fun buildAndSend(
            seq: Int,
            timestampMs: Long,
            speedKph: Int,
            gear: Int,
            dataStatus: Int,
        ): Boolean {
            val payload = SomeipPayloadCodec.encode(seq, timestampMs, speedKph, gear, dataStatus)
            val submitted = nativeSendState(payload)
            Log.d(
                TAG,
                ((if (submitted) "submitted" else "skipped") +
                    " seq=" +
                    Integer.toUnsignedLong(seq) +
                    " speed=" +
                    speedKph +
                    " gear=" +
                    gear +
                    " dataStatus=" +
                    dataStatus),
            )
            return submitted
        }

        @get:JvmStatic
        val isAvailable: Boolean
            get() {
                return nativeIsAvailable()
            }

        @Synchronized
        @JvmStatic
        fun setListener(l: Listener?) {
            sListener = l
        }

        @Synchronized
        @JvmStatic
        fun clearListener(listener: Listener?) {
            if (sListener === listener) sListener = null
        }

        /* JNI supplies an owned byte array; delivery is serialized with availability on the UI thread. */
        @JvmStatic
        fun onNativeVehicleEvent(payload: ByteArray?) {
            val listener = sListener
            UI.post {
                if (listener != null && listener === sListener) listener!!.onVehicleEvent(payload)
            }
        }

        /* native 线程回调入口；只做转发，不碰 UI。 */
        @JvmStatic
        fun onNativeEvent(kind: Int, arg: Int) {
            val l = sListener
            when (kind) {
                EVT_REGISTERED -> {
                    Log.i(TAG, "native: registered")
                    UI.post { if (l != null && l === sListener) l!!.onRegistered() }
                }
                EVT_AVAILABLE -> UI.post { if (l != null && l === sListener) l!!.onAvailable(true) }
                EVT_UNAVAILABLE ->
                    UI.post { if (l != null && l === sListener) l!!.onAvailable(false) }
                EVT_RESPONSE_OK ->
                    UI.post { if (l != null && l === sListener) l!!.onResponse(true, arg) }
                EVT_RESPONSE_ERROR -> {
                    Log.w(TAG, "native: response error code=" + arg)
                    UI.post { if (l != null && l === sListener) l!!.onResponse(false, arg) }
                }
                EVT_STOPPED -> {
                    Log.i(TAG, "native: stopped")
                    UI.post { if (l != null && l === sListener) l!!.onStopped() }
                }
                else -> Log.w(TAG, "native: unknown event kind=" + kind)
            }
        }
    }
}
