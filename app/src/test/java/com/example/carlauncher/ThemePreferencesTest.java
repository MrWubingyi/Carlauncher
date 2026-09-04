package com.example.carlauncher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * ThemePreferences 中持久化值与 UI 位置之间映射的纯逻辑测试。
 */
public class ThemePreferencesTest {

    @Test
    public void themeToPosition_mapsThemes() {
        assertEquals(0, ThemePreferences.themeToPosition(ThemePreferences.THEME_COLOR_SYSTEM));
        assertEquals(1, ThemePreferences.themeToPosition(ThemePreferences.THEME_COLOR_LIGHT));
        assertEquals(2, ThemePreferences.themeToPosition(ThemePreferences.THEME_COLOR_DARK));
    }

    @Test
    public void themeToPosition_invalidOrNullDefaultsToSystem() {
        assertEquals(0, ThemePreferences.themeToPosition(null));
        assertEquals(0, ThemePreferences.themeToPosition("bogus"));
    }

    @Test
    public void positionToColorTheme_mapsPositions() {
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM, ThemePreferences.positionToColorTheme(0));
        assertEquals(ThemePreferences.THEME_COLOR_LIGHT, ThemePreferences.positionToColorTheme(1));
        assertEquals(ThemePreferences.THEME_COLOR_DARK, ThemePreferences.positionToColorTheme(2));
    }

    @Test
    public void positionToColorTheme_outOfRangeDefaultsToSystem() {
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM, ThemePreferences.positionToColorTheme(3));
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM, ThemePreferences.positionToColorTheme(-1));
    }

    @Test
    public void themeMapping_roundTrips() {
        for (int position = 0; position <= 2; position++) {
            String theme = ThemePreferences.positionToColorTheme(position);
            assertEquals(position, ThemePreferences.themeToPosition(theme));
        }
    }
}
