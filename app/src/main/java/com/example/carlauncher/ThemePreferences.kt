package com.example.carlauncher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

class ThemePreferences private constructor() {
    companion object {
        private const val TAG: String = "THEME_PREFERENCES"
        private const val PREFERENCES_NAME: String = "car_launcher_settings"
        private const val KEY_COLOR_THEME: String = "color_theme"
        private const val KEY_WELCOME_ENABLED: String = "welcome_theme"
        const val THEME_COLOR_SYSTEM: String = "system"
        const val THEME_COLOR_LIGHT: String = "light"
        const val THEME_COLOR_DARK: String = "dark"

        private const val DEFAULT_WELCOME_ENABLED: Boolean = true

        @JvmStatic
        fun getColorTheme(context: Context?): String? {
            val storedTheme = preferences(context)!!.getString(KEY_COLOR_THEME, THEME_COLOR_SYSTEM)
            Log.i(TAG, "Loaded theme=" + storedTheme)
            if (!isColorValid(storedTheme)) {
                Log.w(TAG, "Invalid stored theme, fallback to system: " + storedTheme)
                preferences(context)!!.edit().remove(KEY_COLOR_THEME).apply()
                return THEME_COLOR_SYSTEM
            }
            return storedTheme
        }

        @JvmStatic
        fun saveAndApplyColorTheme(context: Context?, theme: String?) {
            val safeTheme = if (isColorValid(theme)) theme else THEME_COLOR_SYSTEM
            preferences(context)!!.edit().putString(KEY_COLOR_THEME, safeTheme).apply()
            Log.i(TAG, "Saved theme=" + safeTheme)
            applyColorTheme(safeTheme)
        }

        @JvmStatic
        fun applySavedTheme(context: Context?) {
            applyColorTheme(getColorTheme(context))
        }

        @JvmStatic
        fun themeToPosition(theme: String?): Int {
            if (THEME_COLOR_LIGHT == theme) {
                return 1
            }
            if (THEME_COLOR_DARK == theme) {
                return 2
            }
            return 0
        }

        @JvmStatic
        fun positionToColorTheme(position: Int): String? {
            if (position == 1) {
                return THEME_COLOR_LIGHT
            }
            if (position == 2) {
                return THEME_COLOR_DARK
            }
            return THEME_COLOR_SYSTEM
        }

        @JvmStatic
        private fun applyColorTheme(theme: String?) {
            val nightMode: Int
            if (THEME_COLOR_LIGHT == theme) {
                nightMode = AppCompatDelegate.MODE_NIGHT_NO
            } else if (THEME_COLOR_DARK == theme) {
                nightMode = AppCompatDelegate.MODE_NIGHT_YES
            } else {
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            Log.i(TAG, "Apply theme=" + theme + ", nightMode=" + nightMode)
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        @JvmStatic
        private fun isColorValid(theme: String?): Boolean =
            (THEME_COLOR_SYSTEM == theme || THEME_COLOR_LIGHT == theme || THEME_COLOR_DARK == theme)

        @JvmStatic
        fun isWelcomeEnabled(context: Context?): Boolean {
            val enabled =
                preferences(context)!!.getBoolean(
                    KEY_WELCOME_ENABLED,
                    DEFAULT_WELCOME_ENABLED,
                )
            Log.i(TAG, "Loaded welcome enabled=" + enabled)
            return enabled
        }

        @JvmStatic
        fun setWelcomeEnabled(context: Context?, enabled: Boolean) {
            preferences(context)!!.edit().putBoolean(KEY_WELCOME_ENABLED, enabled).apply()

            Log.i(TAG, "Saved welcome enabled=" + enabled)
        }

        @JvmStatic
        private fun preferences(context: Context?): SharedPreferences? =
            context!!
                .getApplicationContext()
                .getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                )

        @JvmStatic
        fun resetToDefaults(context: Context?) {
            preferences(context)!!
                .edit()
                .remove(KEY_COLOR_THEME)
                .remove(KEY_WELCOME_ENABLED)
                .apply()

            Log.i(
                TAG,
                ("Settings reset: colorTheme=" +
                    THEME_COLOR_SYSTEM +
                    ", welcomeEnabled=" +
                    DEFAULT_WELCOME_ENABLED),
            )
        }
    }
}
