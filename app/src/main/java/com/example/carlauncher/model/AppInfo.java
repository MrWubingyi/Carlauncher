package com.example.carlauncher.model;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public final class AppInfo {
    private final String name;
    private final String packageName;
    private final Drawable icon;
    private final Intent launchIntent;

    public AppInfo(String name, String packageName, Drawable icon, Intent launchIntent) {
        this.name = name;
        this.packageName = packageName;
        this.icon = icon;
        this.launchIntent = launchIntent;
    }

    public String getName() {
        return name;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }
    public Intent getLaunchIntent() {
        return launchIntent;
    }
}
