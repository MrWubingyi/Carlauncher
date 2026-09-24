package com.example.carlauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** CarLauncherApplication 的 Instrumentation 冒烟测试： onCreate 应用已存主题且不抛异常。 */
@RunWith(AndroidJUnit4::class)
open class CarLauncherApplicationTest {

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
