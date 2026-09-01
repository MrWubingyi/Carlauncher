package com.example.carlauncher;

import android.app.Application;

public class CarLauncherApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemePreferences.applySavedTheme(this);
    }
}
