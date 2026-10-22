package com.zqautomotive.vehicle.someip.android

import com.example.someip.SomeIpClientConfig
import java.io.File
import org.json.JSONObject

/** Rebuilds native input from the selected JSON source on each start; never rewrites the source. */
internal class SomeIpConfigPreparer(
    private val directory: File,
    private val readBundledConfig: () -> String,
    private val findLocalIpv4: () -> String?,
) {
    fun prepare(): File {
        val override = File(directory, "vsomeip-client.override.json")
        val source = if (override.exists()) override.readText(Charsets.UTF_8) else readBundledConfig()
        val json = JSONObject(source)
        val unicast = json.opt("unicast")
        require(unicast is String && unicast.isNotBlank()) { "unicast must be an IP address or auto" }
        if (unicast == "auto") {
            val address = findLocalIpv4()
            check(!address.isNullOrBlank()) { "No usable IPv4 address for unicast=auto" }
            json.put("unicast", address)
        }
        val runtime = File(directory, "vsomeip-runtime.json")
        val resolved = json.toString(2)
        // Validate before opening output so malformed input cannot replace a usable runtime file.
        SomeIpClientConfig.fromJson(resolved, runtime.absolutePath)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create SOME/IP configuration directory" }
        runtime.writeText(resolved, Charsets.UTF_8)
        return runtime
    }
}
