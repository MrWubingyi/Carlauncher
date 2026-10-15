package com.example.someip

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SomeIpJsonConfigTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun json() = JSONObject(
        """{
          "applications": [{"name":"test-client","id":"0x5678"}],
          "unicast":"192.0.2.2",
          "client-endpoint": {
            "application":"test-client", "service":"0x1357", "instance":9320,
            "event":"0X8012", "event-group":"42", "method":"0x1234",
            "max-event-payload-bytes":8192, "max-request-payload-bytes":128
          }
        }""",
    )

    private fun parse(root: JSONObject) = SomeIpClientConfig.fromJson(root.toString(), "/private/config.json")

    @Test fun readsEveryEndpointFieldAndSupportsNumericDecimalAndHexValues() {
        assertEquals(
            SomeIpService(0x1357, 9320, 0x8012, 42, 0x1234, "test-client", 8192, 128),
            parse(json()).service,
        )
    }

    @Test fun fileIsRereadInsteadOfUsingThePreviousEndpoint() {
        val file = temporary.newFile("config.json")
        val root = json()
        file.writeText(root.toString())
        assertEquals(0x1357, SomeIpClientConfig.fromFile(file).service.serviceId)
        root.getJSONObject("client-endpoint").put("service", "0x4567").put("method", 123)
        file.writeText(root.toString())
        val config = SomeIpClientConfig.fromFile(file)
        assertEquals(file.absolutePath, config.configPath)
        assertEquals(0x4567, config.service.serviceId)
        assertEquals(123, config.service.methodId)
    }

    @Test fun missingEndpointOrRequiredFieldsNeverFallsBackToDefaults() {
        val root = json()
        root.remove("client-endpoint")
        assertThrows(IllegalArgumentException::class.java) { parse(root) }
        for (key in listOf("application", "service", "instance", "event", "event-group", "method",
            "max-event-payload-bytes", "max-request-payload-bytes")) {
            val incomplete = json()
            incomplete.getJSONObject("client-endpoint").remove(key)
            assertThrows("missing $key", IllegalArgumentException::class.java) { parse(incomplete) }
        }
    }

    @Test fun fractionsBooleansNullAndOverflowAreRejectedRatherThanCoerced() {
        for (value in listOf(1.5, true, JSONObject.NULL, "0x", "1.5", "1e3", "-1",
            4_294_967_297L, "0x10000000000000000", "999999999999999999999")) {
            val root = json()
            root.getJSONObject("client-endpoint").put("service", value)
            assertThrows("invalid $value", IllegalArgumentException::class.java) { parse(root) }
        }
    }

    @Test fun protocolAndPayloadBoundsAreEnforcedAfterJsonParsing() {
        for ((key, value) in listOf("service" to "0xffff", "instance" to -1,
            "event" to "0x1234", "event-group" to "0x10000", "method" to "0x8000",
            "max-event-payload-bytes" to 0, "max-request-payload-bytes" to 1_048_577)) {
            val root = json()
            root.getJSONObject("client-endpoint").put(key, value)
            assertThrows(key, IllegalArgumentException::class.java) { parse(root) }
        }
    }

    @Test fun selectedApplicationMustExistExactlyOnce() {
        val missing = json()
        missing.getJSONObject("client-endpoint").put("application", "not-declared")
        assertThrows(IllegalArgumentException::class.java) { parse(missing) }
        val duplicate = json()
        duplicate.getJSONArray("applications").put(JSONObject().put("name", "test-client"))
        assertThrows(IllegalArgumentException::class.java) { parse(duplicate) }
        val multiple = json()
        multiple.getJSONArray("applications").put(JSONObject().put("name", "other-client"))
        assertEquals("test-client", parse(multiple).service.applicationName)
    }

    @Test fun applicationMustBeAStringAndCannotContainANativeTerminator() {
        for (value in listOf(123, "", " ", "test-client\u0000other")) {
            val root = json()
            root.getJSONObject("client-endpoint").put("application", value)
            assertThrows(IllegalArgumentException::class.java) { parse(root) }
        }
    }

    @Test fun malformedJsonAndWrongSectionTypesFailWithConfigurationErrors() {
        for (input in listOf("not-json", "{", "[]", "{\"client-endpoint\":[]}")) {
            assertThrows(IllegalArgumentException::class.java) {
                SomeIpClientConfig.fromJson(input, "/private/config.json")
            }
        }
    }

    @Test fun nativeNetworkConfigurationIsNotMutatedByParsing() {
        val file = temporary.newFile("config.json")
        val text = json().put("service-discovery", JSONObject().put("enable", false)).toString(2)
        file.writeText(text)
        SomeIpClientConfig.fromFile(file)
        assertEquals(text, file.readText())
    }
}
