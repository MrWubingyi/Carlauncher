package com.example.carlauncher

import android.app.Application

open class CarLauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreferences.applySavedTheme(this)
    }
}
