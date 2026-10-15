package com.example.vehicle.someip.android

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Android filesystem and local-address policy for the PoC host. */
internal class SomeIpConfigFile(private val context: Context?) {
    @Throws(Exception::class)
    fun prepare(): File {
        val application = requireNotNull(context) { "A Context is required to prepare SOME/IP configuration" }
        return SomeIpConfigPreparer(
            File(application.filesDir, "vsomeip"),
            { application.assets.open("vsomeip-client.json").bufferedReader(Charsets.UTF_8).use { it.readText() } },
            ::findLocalIpv4,
        ).prepare()
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
