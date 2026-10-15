package com.example.vehicle.someip.android

import com.example.someip.SomeIpClientConfig
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SomeIpConfigPreparerTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun bundled(): String = File("src/main/assets/vsomeip-client.json").readText()

    @Test fun bundledConfigurationResolvesAutoAndKeepsEndpointAndSourceUnchanged() {
        val source = bundled()
        val directory = temporary.newFolder()
        val runtime = SomeIpConfigPreparer(directory, { source }, { "10.0.2.16" }).prepare()
        assertEquals("10.0.2.16", JSONObject(runtime.readText()).getString("unicast"))
        assertEquals("auto", JSONObject(source).getString("unicast"))
        val endpoint = SomeIpClientConfig.fromFile(runtime).service
        assertEquals(SomeIpClientConfig.fromJson(source, runtime.absolutePath).service, endpoint)
    }

    @Test fun nextStartUsesChangedJsonInsteadOfStaleRuntimeOrLegacyCopy() {
        val directory = temporary.newFolder()
        val legacy = File(directory, "vsomeip-client.json").apply { writeText("legacy contents") }
        var source = bundled()
        val preparer = SomeIpConfigPreparer(directory, { source }, { "10.0.2.16" })
        val runtime = preparer.prepare()
        val changed = JSONObject(source)
        changed.getJSONObject("client-endpoint").put("service", "0x4321")
        source = changed.toString()
        preparer.prepare()
        assertEquals(0x4321, SomeIpClientConfig.fromFile(runtime).service.serviceId)
        assertEquals("legacy contents", legacy.readText())
    }

    @Test fun explicitOverrideWinsAndStaticUnicastNeverInvokesAddressSelection() {
        val directory = temporary.newFolder()
        val override = File(directory, "vsomeip-client.override.json")
        val source = JSONObject(bundled()).put("unicast", "192.0.2.42")
        source.getJSONArray("services").getJSONObject(0).put("unicast", "192.0.2.99")
        source.getJSONObject("client-endpoint").put("event", "0x8020")
        val text = source.toString(2)
        override.writeText(text)
        val runtime = SomeIpConfigPreparer(directory,
            { error("override must take priority") }, { error("static IP must be retained") }).prepare()
        val actual = JSONObject(runtime.readText())
        assertEquals("192.0.2.42", actual.getString("unicast"))
        assertEquals("192.0.2.99", actual.getJSONArray("services").getJSONObject(0).getString("unicast"))
        assertEquals(0x8020, SomeIpClientConfig.fromFile(runtime).service.eventId)
        assertEquals(text, override.readText())
    }

    @Test fun invalidOverrideFailsWithoutFallingBackOrReplacingRuntime() {
        val directory = temporary.newFolder()
        val runtime = SomeIpConfigPreparer(directory, ::bundled, { "10.0.2.16" }).prepare()
        val previous = runtime.readText()
        File(directory, "vsomeip-client.override.json").writeText("{\"unicast\":\"192.0.2.42\"}")
        assertThrows(IllegalArgumentException::class.java) {
            SomeIpConfigPreparer(directory, { error("must not fall back") }, { null }).prepare()
        }
        assertEquals(previous, runtime.readText())
    }

    @Test fun unavailableAutomaticAddressFailsBeforeReplacingRuntime() {
        val directory = temporary.newFolder()
        val runtime = SomeIpConfigPreparer(directory, ::bundled, { "10.0.2.16" }).prepare()
        val previous = runtime.readText()
        assertThrows(IllegalStateException::class.java) {
            SomeIpConfigPreparer(directory, ::bundled, { null }).prepare()
        }
        assertEquals(previous, runtime.readText())
    }

    @Test fun unicastMustBeExplicitlyConfigured() {
        for (value in listOf(JSONObject.NULL, true, "")) {
            val source = JSONObject(bundled()).put("unicast", value).toString()
            assertThrows(IllegalArgumentException::class.java) {
                SomeIpConfigPreparer(temporary.newFolder(), { source }, { "10.0.2.16" }).prepare()
            }
        }
    }
}
