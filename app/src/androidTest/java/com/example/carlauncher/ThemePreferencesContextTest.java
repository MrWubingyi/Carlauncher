package com.example.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ThemePreferences 的 SharedPreferences 持久化与主题应用行为（Instrumentation）。
 * 纯位置映射已在 {@code ThemePreferencesTest} 中覆盖。
 */
@RunWith(AndroidJUnit4.class)
public class ThemePreferencesContextTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ThemePreferences.resetToDefaults(context);
    }

    @Test
    public void getColorTheme_withoutStoredValue_returnsSystemDefault() {
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM,
                ThemePreferences.getColorTheme(context));
    }

    @Test
    public void getColorTheme_withInvalidStoredValue_fallsBackAndClearsKey() {
        SharedPreferences prefs = context.getSharedPreferences(
                "car_launcher_settings", Context.MODE_PRIVATE);
        prefs.edit().putString("color_theme", "not-a-theme").commit();

        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM,
                ThemePreferences.getColorTheme(context));
        assertFalse("非法存储值应被清除",
                prefs.contains("color_theme"));
    }

    @Test
    public void saveAndApply_persistsLightAndDarkThemes() {
        ThemePreferences.saveAndApplyColorTheme(
                context, ThemePreferences.THEME_COLOR_LIGHT);
        assertEquals(ThemePreferences.THEME_COLOR_LIGHT,
                ThemePreferences.getColorTheme(context));

        ThemePreferences.saveAndApplyColorTheme(
                context, ThemePreferences.THEME_COLOR_DARK);
        assertEquals(ThemePreferences.THEME_COLOR_DARK,
                ThemePreferences.getColorTheme(context));
    }

    @Test
    public void saveAndApply_withInvalidTheme_persistsSystemFallback() {
        ThemePreferences.saveAndApplyColorTheme(context, "bogus");
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM,
                ThemePreferences.getColorTheme(context));
    }

    @Test
    public void saveAndApply_appliesNightModeToAppCompat() {
        ThemePreferences.saveAndApplyColorTheme(
                context, ThemePreferences.THEME_COLOR_LIGHT);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.getDefaultNightMode());

        ThemePreferences.saveAndApplyColorTheme(
                context, ThemePreferences.THEME_COLOR_DARK);
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void applySavedTheme_appliesStoredTheme() {
        ThemePreferences.saveAndApplyColorTheme(
                context, ThemePreferences.THEME_COLOR_DARK);

        // 重置为跟随系统后再按已存主题应用
        AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        ThemePreferences.applySavedTheme(context);

        assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void welcomeEnabled_roundTripsPersistedValue() {
        assertTrue("默认应启用欢迎提示",
                ThemePreferences.isWelcomeEnabled(context));

        ThemePreferences.setWelcomeEnabled(context, false);
        assertFalse(ThemePreferences.isWelcomeEnabled(context));

        ThemePreferences.setWelcomeEnabled(context, true);
        assertTrue(ThemePreferences.isWelcomeEnabled(context));
    }

    @Test
    public void resetToDefaults_clearsThemeAndWelcome() {
        ThemePreferences.saveAndApplyColorTheme(
                context, ThemePreferences.THEME_COLOR_DARK);
        ThemePreferences.setWelcomeEnabled(context, false);

        ThemePreferences.resetToDefaults(context);

        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM,
                ThemePreferences.getColorTheme(context));
        assertTrue(ThemePreferences.isWelcomeEnabled(context));
    }
}

