package com.example.carlauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.carlauncher.data.VehicleServiceDependencies
import com.example.vehicle.someip.adapter.SomeipConnectionMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** CarLauncherApplication 的 Instrumentation 冒烟测试： onCreate 应用已存主题且不抛异常。 */
@RunWith(AndroidJUnit4::class)
open class CarLauncherApplicationTest {

    @Test
    fun applicationProvidesAnUnstartedSourceThroughTheDependencyInterface() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dependencies = context as VehicleServiceDependencies
        val monitor = SomeipConnectionMonitor()
        val source = dependencies.vehicleEventSourceFactory.create(context, monitor)
        try {
            assertFalse(source.isRunning)
            assertEquals(SomeipConnectionMonitor.State.STOPPED, monitor.snapshot().state)
        } finally {
            source.stop()
        }
    }

    @Test
    open fun applicationContext_isCarLauncherApplication_andAppliesSavedTheme() {
        val context = ApplicationProvider.getApplicationContext<Context?>()

        assertTrue(context is CarLauncherApplication)
        ThemePreferences.resetToDefaults(context)
        ThemePreferences.applySavedTheme(context)
        assertEquals(
            ThemePreferences.THEME_COLOR_SYSTEM,
            ThemePreferences.getColorTheme(context),
        )
        assertTrue(ThemePreferences.isWelcomeEnabled(context))
    }
}
