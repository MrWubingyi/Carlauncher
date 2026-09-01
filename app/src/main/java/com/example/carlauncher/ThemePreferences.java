package com.example.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePreferences {
    private static final String TAG = "THEME_PREFERENCES";
    private static final String PREFERENCES_NAME = "car_launcher_settings";
    private static final String KEY_THEME = "theme";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_UNKNOWN = "unknown";

    private ThemePreferences() {
    }

    public static String getTheme(Context context) {
        String storedTheme = preferences(context).getString(KEY_THEME, THEME_SYSTEM);
        Log.i(TAG, "Loaded theme=" + storedTheme);
        if (!isValid(storedTheme)) {
            Log.w(TAG, "Invalid stored theme, fallback to system: " + storedTheme);
            preferences(context).edit().remove(KEY_THEME).apply();
            return THEME_SYSTEM;
        }
        return storedTheme;
    }

    public static void saveAndApplyTheme(Context context, String theme) {
        String safeTheme = isValid(theme) ? theme : THEME_SYSTEM;
        preferences(context).edit().putString(KEY_THEME, safeTheme).apply();
        Log.i(TAG, "Saved theme=" + safeTheme);
        applyTheme(safeTheme);
    }
    public static void saveRawThemeForTest(Context context, String theme) {


        preferences(context)
                .edit()
                .putString(KEY_THEME, theme)
                .commit();

        Log.i(TAG, "Injected raw theme for test=" + theme);
    }
    public static void applySavedTheme(Context context) {
        applyTheme(getTheme(context));
    }

    public static int themeToPosition(String theme) {
        if (THEME_LIGHT.equals(theme)) {
            return 1;
        }
        if (THEME_DARK.equals(theme)) {
            return 2;
        }
        if(THEME_UNKNOWN.equals(theme)){
            return 3;
        }
        return 0;
    }

    public static String positionToTheme(int position) {
        if (position == 1) {
            return THEME_LIGHT;
        }
        if (position == 2) {
            return THEME_DARK;
        }
        if (position== 3){
            return THEME_UNKNOWN;
        }
        return THEME_SYSTEM;
    }

    private static void applyTheme(String theme) {
        int nightMode;
        if (THEME_LIGHT.equals(theme)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (THEME_DARK.equals(theme)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        Log.i(TAG, "Apply theme=" + theme + ", nightMode=" + nightMode);
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private static boolean isValid(String theme) {
        return THEME_SYSTEM.equals(theme)
                || THEME_LIGHT.equals(theme)
                || THEME_DARK.equals(theme);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }
}
