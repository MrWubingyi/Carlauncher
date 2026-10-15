package com.example.someip.internal

import com.example.someip.SomeIpClientConfig
import com.example.someip.SomeIpService
import org.json.JSONException
import org.json.JSONObject

/** The extension configures our client; vSomeIP continues to consume the native JSON sections. */
internal object JsonClientConfig {
    fun parse(text: String, configPath: String): SomeIpClientConfig {
        try {
            val root = JSONObject(text)
            val endpoint = root.getJSONObject("client-endpoint")
            val application = endpoint.requiredString("application")
            val applications = root.getJSONArray("applications")
            val matches = (0 until applications.length()).count {
                applications.getJSONObject(it).opt("name") == application
            }
            require(matches == 1) { "client-endpoint.application must match exactly one applications[].name" }
            return SomeIpClientConfig(
                configPath,
                SomeIpService(
                    serviceId = endpoint.requiredInt("service"),
                    instanceId = endpoint.requiredInt("instance"),
                    eventId = endpoint.requiredInt("event"),
                    eventGroupId = endpoint.requiredInt("event-group"),
                    methodId = endpoint.requiredInt("method"),
                    applicationName = application,
                    maxEventPayloadBytes = endpoint.requiredInt("max-event-payload-bytes"),
                    maxRequestPayloadBytes = endpoint.requiredInt("max-request-payload-bytes"),
                ),
            )
        } catch (error: JSONException) {
            throw IllegalArgumentException("Invalid SOME/IP client JSON: ${error.message}", error)
        }
    }

    private fun JSONObject.requiredString(key: String): String {
        val value = opt(key)
        require(value is String && value.isNotBlank()) { "client-endpoint.$key must be a nonblank string" }
        return value
    }

    private fun JSONObject.requiredInt(key: String): Int {
        val value = opt(key)
        val number = when (value) {
            is Int -> value.toLong()
            is Long -> value
            is String -> when {
                value.matches(Regex("0[xX][0-9a-fA-F]+")) -> value.substring(2).toLongOrNull(16)
                value.matches(Regex("[0-9]+")) -> value.toLongOrNull()
                else -> null
            }
            else -> null
        }
        require(number != null && number in 0..Int.MAX_VALUE.toLong()) {
            "client-endpoint.$key must be a nonnegative integer or decimal/0x string"
        }
        return number.toInt()
    }
}
