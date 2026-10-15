package com.example.someip

import com.example.someip.internal.JsonClientConfig
import java.io.File

/** Protocol identifiers are unsigned 16-bit values; wildcard identifiers are not accepted. */
data class SomeIpService(
    val serviceId: Int,
    val instanceId: Int,
    val eventId: Int,
    val eventGroupId: Int,
    val methodId: Int,
    val applicationName: String,
    val maxEventPayloadBytes: Int = 4096,
    val maxRequestPayloadBytes: Int = 4096,
) {
    init {
        require(serviceId in 0..0xfffe) { "serviceId must be 0..0xfffe" }
        require(instanceId in 0..0xfffe) { "instanceId must be 0..0xfffe" }
        require(eventGroupId in 0..0xfffe) { "eventGroupId must be 0..0xfffe" }
        require(eventId in 0x8000..0xfffe) { "eventId must be 0x8000..0xfffe" }
        require(methodId in 0..0x7fff) { "methodId must be 0..0x7fff" }
        require(applicationName.isNotBlank() && '\u0000' !in applicationName) {
            "applicationName must be nonblank and contain no NUL"
        }
        require(maxEventPayloadBytes in 1..MAX_PAYLOAD_BYTES) { "Invalid event payload limit" }
        require(maxRequestPayloadBytes in 1..MAX_PAYLOAD_BYTES) { "Invalid request payload limit" }
    }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 1024 * 1024
    }
}

/** The caller supplies a readable vSomeIP JSON file; asset and IP selection remain caller policy. */
data class SomeIpClientConfig(val configPath: String, val service: SomeIpService) {
    init {
        require(configPath.isNotBlank() && '\u0000' !in configPath) {
            "configPath must be nonblank and contain no NUL"
        }
    }

    companion object {
        /** Reads the client-endpoint extension and validates its application declaration. */
        @JvmStatic
        fun fromFile(file: File): SomeIpClientConfig = fromJson(file.readText(Charsets.UTF_8), file.absolutePath)

        /** configPath names the native JSON file containing this same configuration. */
        @JvmStatic
        fun fromJson(json: String, configPath: String): SomeIpClientConfig = JsonClientConfig.parse(json, configPath)
    }
}
