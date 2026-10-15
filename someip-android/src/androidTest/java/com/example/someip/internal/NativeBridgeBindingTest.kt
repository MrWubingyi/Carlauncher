package com.example.someip.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises JNI linkage and fail-closed boundaries without a peer or routing session. */
@RunWith(AndroidJUnit4::class)
class NativeBridgeBindingTest {
    @Test
    fun invalidStartAndInactiveOperationsUseLibraryJniBindings() {
        NativeBridge.ensureLoaded()
        try {
            assertFalse(NativeBridge.nativeStart("", "test", 1, 1, 0x8001, 1, 1, 4096, 4096))
            assertFalse(NativeBridge.nativeStart("/nonexistent/config.json", "test", 0xffff, 1, 0x8001, 1, 1, 4096, 4096))
            NativeBridge.nativeNetworkState(null, null, false, false)
            assertFalse(NativeBridge.nativeIsAvailable())
            assertFalse(NativeBridge.nativeSend(null))
            assertFalse(NativeBridge.nativeSend(byteArrayOf()))
            // Resolve both static callback methods while no managed client owns a listener.
            NativeBridge.onNativeEvent(0, 0)
            NativeBridge.onNativePayload(byteArrayOf(0, -1))
        } finally {
            NativeBridge.nativeStop()
            NativeBridge.nativeStop()
        }
    }
}
