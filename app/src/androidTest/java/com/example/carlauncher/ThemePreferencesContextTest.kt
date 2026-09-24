package com.example.carlauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ThemePreferences 的 SharedPreferences 持久化与主题应用行为（Instrumentation）。 纯位置映射已在 `ThemePreferencesTest`
 * 中覆盖。
 */
@RunWith(AndroidJUnit4::class)
open class ThemePreferencesContextTest {

    private var context: Context? = null

    @Before
    open fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context?>()
        ThemePreferences.resetToDefaults(context)
    }

    @Test
    open fun getColorTheme_withoutStoredValue_returnsSystemDefault() {
        assertEquals(
            ThemePreferences.THEME_COLOR_SYSTEM,
            ThemePreferences.getColorTheme(context),
        )
    }

    @Test
    open fun getColorTheme_withInvalidStoredValue_fallsBackAndClearsKey() {
        val prefs =
            context!!.getSharedPreferences(
                "car_launcher_settings",
                Context.MODE_PRIVATE,
            )
        prefs!!.edit().putString("color_theme", "not-a-theme").commit()

        assertEquals(
            ThemePreferences.THEME_COLOR_SYSTEM,
            ThemePreferences.getColorTheme(context),
        )
        assertFalse(
            "非法存储值应被清除",
            prefs!!.contains("color_theme"),
        )
    }

    @Test
    open fun saveAndApply_persistsLightAndDarkThemes() {
        ThemePreferences.saveAndApplyColorTheme(
            context,
            ThemePreferences.THEME_COLOR_LIGHT,
        )
        assertEquals(
            ThemePreferences.THEME_COLOR_LIGHT,
            ThemePreferences.getColorTheme(context),
        )

        ThemePreferences.saveAndApplyColorTheme(
            context,
            ThemePreferences.THEME_COLOR_DARK,
        )
        assertEquals(
            ThemePreferences.THEME_COLOR_DARK,
            ThemePreferences.getColorTheme(context),
        )
    }

    @Test
    open fun saveAndApply_withInvalidTheme_persistsSystemFallback() {
        ThemePreferences.saveAndApplyColorTheme(context, "bogus")
        assertEquals(
            ThemePreferences.THEME_COLOR_SYSTEM,
            ThemePreferences.getColorTheme(context),
        )
    }

    @Test
    open fun saveAndApply_appliesNightModeToAppCompat() {
        ThemePreferences.saveAndApplyColorTheme(
            context,
            ThemePreferences.THEME_COLOR_LIGHT,
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO.toLong(),
            AppCompatDelegate.getDefaultNightMode().toLong(),
        )

        ThemePreferences.saveAndApplyColorTheme(
            context,
            ThemePreferences.THEME_COLOR_DARK,
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES.toLong(),
            AppCompatDelegate.getDefaultNightMode().toLong(),
        )
    }

    @Test
    open fun applySavedTheme_appliesStoredTheme() {
        ThemePreferences.saveAndApplyColorTheme(
            context,
            ThemePreferences.THEME_COLOR_DARK,
        )

        // 重置为跟随系统后再按已存主题应用
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemePreferences.applySavedTheme(context)

        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES.toLong(),
            AppCompatDelegate.getDefaultNightMode().toLong(),
        )
    }

    @Test
    open fun welcomeEnabled_roundTripsPersistedValue() {
        assertTrue(
            "默认应启用欢迎提示",
            ThemePreferences.isWelcomeEnabled(context),
        )

        ThemePreferences.setWelcomeEnabled(context, false)
        assertFalse(ThemePreferences.isWelcomeEnabled(context))

        ThemePreferences.setWelcomeEnabled(context, true)
        assertTrue(ThemePreferences.isWelcomeEnabled(context))
    }

    @Test
    open fun resetToDefaults_clearsThemeAndWelcome() {
        ThemePreferences.saveAndApplyColorTheme(
            context,
            ThemePreferences.THEME_COLOR_DARK,
        )
        ThemePreferences.setWelcomeEnabled(context, false)

        ThemePreferences.resetToDefaults(context)

        assertEquals(
            ThemePreferences.THEME_COLOR_SYSTEM,
            ThemePreferences.getColorTheme(context),
        )
        assertTrue(ThemePreferences.isWelcomeEnabled(context))
    }
}
