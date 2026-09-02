package com.example.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePreferences {
    private static final String TAG = "THEME_PREFERENCES";
    private static final String PREFERENCES_NAME = "car_launcher_settings";
    private static final String KEY_COLOR_THEME = "color_theme";
    private static final String KEY_WELCOME_ENABLED = "welcome_theme";
    public static final String THEME_COLOR_SYSTEM = "system";
    public static final String THEME_COLOR_LIGHT = "light";
    public static final String THEME_COLOR_DARK = "dark";

    private static final boolean DEFAULT_WELCOME_ENABLED = true;

    private ThemePreferences() {
    }

    public static String getColorTheme(Context context) {
        String storedTheme = preferences(context).getString(KEY_COLOR_THEME, THEME_COLOR_SYSTEM);
        Log.i(TAG, "Loaded theme=" + storedTheme);
        if (!isColorValid(storedTheme)) {
            Log.w(TAG, "Invalid stored theme, fallback to system: " + storedTheme);
            preferences(context).edit().remove(KEY_COLOR_THEME).apply();
            return THEME_COLOR_SYSTEM;
        }
        return storedTheme;
    }

    public static void saveAndApplyColorTheme(Context context, String theme) {
        String safeTheme = isColorValid(theme) ? theme : THEME_COLOR_SYSTEM;
        preferences(context).edit().putString(KEY_COLOR_THEME, safeTheme).apply();
        Log.i(TAG, "Saved theme=" + safeTheme);
        applyColorTheme(safeTheme);
    }

    public static void applySavedTheme(Context context) {
        applyColorTheme(getColorTheme(context));
    }

    public static int themeToPosition(String theme) {
        if (THEME_COLOR_LIGHT.equals(theme)) {
            return 1;
        }
        if (THEME_COLOR_DARK.equals(theme)) {
            return 2;
        }
        return 0;
    }

    public static String positionToColorTheme(int position) {
        if (position == 1) {
            return THEME_COLOR_LIGHT;
        }
        if (position == 2) {
            return THEME_COLOR_DARK;
        }
        return THEME_COLOR_SYSTEM;
    }

    private static void applyColorTheme(String theme) {
        int nightMode;
        if (THEME_COLOR_LIGHT.equals(theme)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (THEME_COLOR_DARK.equals(theme)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        Log.i(TAG, "Apply theme=" + theme + ", nightMode=" + nightMode);
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private static boolean isColorValid(String theme) {
        return THEME_COLOR_SYSTEM.equals(theme)
                || THEME_COLOR_LIGHT.equals(theme)
                || THEME_COLOR_DARK.equals(theme);
    }

    public static boolean isWelcomeEnabled(Context context) {
        boolean enabled = preferences(context).getBoolean(
                KEY_WELCOME_ENABLED,
                DEFAULT_WELCOME_ENABLED
        );
        Log.i(TAG, "Loaded welcome enabled=" + enabled);
        return enabled;
    }

    public static void setWelcomeEnabled(Context context, boolean enabled) {
        preferences(context)
                .edit()
                .putBoolean(KEY_WELCOME_ENABLED, enabled)
                .apply();

        Log.i(TAG, "Saved welcome enabled=" + enabled);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }
    public static void resetToDefaults(Context context) {
        preferences(context)
                .edit()
                .remove(KEY_COLOR_THEME)
                .remove(KEY_WELCOME_ENABLED)
                .apply();

        Log.i(
                TAG,
                "Settings reset: colorTheme="
                        + THEME_COLOR_SYSTEM
                        + ", welcomeEnabled="
                        + DEFAULT_WELCOME_ENABLED
        );
    }
}
