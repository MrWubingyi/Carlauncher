package com.example.carlauncher;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * CarLauncherApplication 的 Instrumentation 冒烟测试：
 * onCreate 应用已存主题且不抛异常。
 */
@RunWith(AndroidJUnit4.class)
public class CarLauncherApplicationTest {

    @Test
    public void applicationContext_isCarLauncherApplication_andAppliesSavedTheme() {
        Context context = ApplicationProvider.getApplicationContext();

        assertTrue(context instanceof CarLauncherApplication);
        ThemePreferences.resetToDefaults(context);
        ThemePreferences.applySavedTheme(context);
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM,
                ThemePreferences.getColorTheme(context));
        assertTrue(ThemePreferences.isWelcomeEnabled(context));
    }
}

