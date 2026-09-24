package com.example.carlauncher.someip

import android.content.Context
import android.util.Log
import com.example.carlauncher.model.VehicleState
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Enumeration
import org.json.JSONObject

/** NativeVehicleTransport 的 vSomeIP JNI 实现类。 持有 VsomeipClient，实现 JNI 回调转发、Event 解码校验与状态通知。 */
open class VSomeIpNativeTransport(context: Context?) :
    NativeVehicleTransport, VsomeipClient.Listener {

    private val context: Context?
    @Volatile private var listener: NativeVehicleTransport.Listener? = null
    @Volatile private var stopping: Boolean = false
    private var lastEventTimestamp: Long = 0
    private var lastEventSequence: Long = -1

    override val isAvailable: Boolean
        get() {
            try {
                return VsomeipClient.isAvailable
            } catch (e: UnsatisfiedLinkError) {
                return false
            }
        }

    init {
        this.context = if (context != null) context!!.getApplicationContext() else null
    }

    @Synchronized
    override fun start(): Boolean {
        stopping = false
        try {
            val config = prepareConfigFile()
            VsomeipClient.setListener(this)
            val ok = VsomeipClient.start(context, config.getAbsolutePath())
            Log.i(TAG, "VSomeIpNativeTransport start ok=" + ok)
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VSomeIpNativeTransport", e)
            val l = listener
            if (l != null) {
                l!!.onError(e)
            }
            return false
        }
    }

    @Synchronized
    override fun stop() {
        stopping = true
        try {
            VsomeipClient.clearListener(this)
            VsomeipClient.stop()
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library not available during stop", e)
        } finally {
            lastEventTimestamp = 0
            lastEventSequence = -1
            Log.i(TAG, "VSomeIpNativeTransport stopped")
        }
    }

    @Synchronized
    override fun setListener(listener: NativeVehicleTransport.Listener?) {
        this.listener = listener
    }

    // ---- VsomeipClient.Listener 回调实现 ----

    override fun onAvailable(available: Boolean) {
        if (stopping) {
            return
        }
        if (!available) {
            lastEventTimestamp = 0
            lastEventSequence = -1
        }
        val l = listener
        if (l != null) {
            l!!.onAvailabilityChanged(available)
        }
    }

    override fun onVehicleEvent(payload: ByteArray?) {
        if (stopping) {
            return
        }
        val l = listener
        if (l == null) {
            return
        }
        try {
            val state = VehicleEventCodec.decode(payload)
            // 时间戳与序列号校验，防止乱序或重复数据重放
            if (
                (state!!.timestampMs < lastEventTimestamp ||
                    ((state!!.timestampMs == lastEventTimestamp &&
                        state!!.sequence <= lastEventSequence)))
            ) {
                return
            }
            lastEventTimestamp = state!!.timestampMs
            lastEventSequence = state!!.sequence
            l!!.onVehicleState(state)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid vehicle event payload", e)
            l!!.onError(e)
        }
    }

    override fun onResponse(ok: Boolean, returnCode: Int) {
        if (stopping) {
            return
        }
        if (!ok) {
            val l = listener
            if (l != null) {
                l!!.onError(Exception("SOME/IP response error code=" + returnCode))
            }
        }
    }

    override fun onStopped() {
        if (stopping) {
            return
        }
        val l = listener
        if (l != null) {
            l!!.onAvailabilityChanged(false)
        }
    }

    // ---- 配置与网络工具方法 ----

    @Throws(Exception::class)
    private fun prepareConfigFile(): File {
        val configName = "vsomeip-client.json"
        val dir = File(context!!.getFilesDir(), "vsomeip")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("mkdir vsomeip dir failed")
        }
        val config = File(dir, configName)
        if (!config.exists()) {
            copyAssetToFile(configName, config)
        }

        val localIp = findLocalIpv4()
        if (localIp == null) {
            throw IllegalStateException("no usable IPv4 address")
        }
        rewriteUnicast(config, localIp)
        return config
    }

    @Throws(Exception::class)
    private fun copyAssetToFile(assetName: String, target: File?) {
        context!!.getAssets().open(assetName).use { `in` ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(4096)
                var n = `in`.read(buf)
                while (n > 0) {
                    out.write(buf, 0, n)
                    n = `in`.read(buf)
                }
            }
        }
    }

    private fun findLocalIpv4(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces == null) {
                return null
            }
            var fallback: String? = null
            while (interfaces!!.hasMoreElements()) {
                val networkInterface = interfaces!!.nextElement()
                if (!networkInterface!!.isUp() || networkInterface!!.isLoopback()) {
                    continue
                }
                val addresses = networkInterface!!.getInetAddresses()
                while (addresses!!.hasMoreElements()) {
                    val address = addresses!!.nextElement()
                    if (!(address is Inet4Address) || address!!.isLoopbackAddress()) {
                        continue
                    }
                    val ip = address!!.getHostAddress()
                    if (ip == null) {
                        continue
                    }
                    if (ip!!.startsWith("192.168.")) {
                        return ip
                    }
                    if (fallback == null) {
                        fallback = ip
                    }
                }
            }
            return fallback
        } catch (e: SocketException) {
            Log.e(TAG, "findLocalIpv4 failed", e)
            return null
        }
    }

    companion object {

        private const val TAG: String = "VSOMEIP_TRANSPORT"

        @Throws(Exception::class)
        @JvmStatic
        internal fun rewriteUnicast(config: File?, ip: String?) {
            val content: String
            FileInputStream(config).use { `in` ->
                ByteArrayOutputStream().use { bytes ->
                    val buf = ByteArray(4096)
                    var n = `in`.read(buf)
                    while (n != -1) {
                        bytes.write(buf, 0, n)
                        n = `in`.read(buf)
                    }
                    content = bytes.toString(StandardCharsets.UTF_8!!.name())
                }
            }
            val json = JSONObject(content)
            if (ip == json.optString("unicast")) return
            json.put("unicast", ip)
            FileOutputStream(config).use { out ->
                OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                    writer.write(json.toString(2))
                }
            }
        }
    }
}
