package com.example.carlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

/** ThemePreferences 中持久化值与 UI 位置之间映射的纯逻辑测试。 */
open class ThemePreferencesTest {

    @Test
    open fun themeToPosition_mapsThemes() {
        assertEquals(
            0,
            ThemePreferences.themeToPosition(ThemePreferences.THEME_COLOR_SYSTEM).toLong(),
        )
        assertEquals(
            1,
            ThemePreferences.themeToPosition(ThemePreferences.THEME_COLOR_LIGHT).toLong(),
        )
        assertEquals(
            2,
            ThemePreferences.themeToPosition(ThemePreferences.THEME_COLOR_DARK).toLong(),
        )
    }

    @Test
    open fun themeToPosition_invalidOrNullDefaultsToSystem() {
        assertEquals(0, ThemePreferences.themeToPosition(null).toLong())
        assertEquals(0, ThemePreferences.themeToPosition("bogus").toLong())
    }

    @Test
    open fun positionToColorTheme_mapsPositions() {
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM, ThemePreferences.positionToColorTheme(0))
        assertEquals(ThemePreferences.THEME_COLOR_LIGHT, ThemePreferences.positionToColorTheme(1))
        assertEquals(ThemePreferences.THEME_COLOR_DARK, ThemePreferences.positionToColorTheme(2))
    }

    @Test
    open fun positionToColorTheme_outOfRangeDefaultsToSystem() {
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM, ThemePreferences.positionToColorTheme(3))
        assertEquals(ThemePreferences.THEME_COLOR_SYSTEM, ThemePreferences.positionToColorTheme(-1))
    }

    @Test
    open fun themeMapping_roundTrips() {
        for (position in 0..2) {
            val theme = ThemePreferences.positionToColorTheme(position)
            assertEquals(position.toLong(), ThemePreferences.themeToPosition(theme).toLong())
        }
    }
}
