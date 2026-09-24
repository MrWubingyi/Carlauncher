package com.example.carlauncher.model

import android.content.Intent
import android.graphics.drawable.Drawable

class AppInfo(
    val name: String?,
    val packageName: String?,
    val icon: Drawable?,
    val launchIntent: Intent?,
)
