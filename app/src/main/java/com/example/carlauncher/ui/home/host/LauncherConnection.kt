package com.example.carlauncher.ui.home.host

/** Host binding is separate from the foreground service's lifetime. */
interface LauncherConnection {
    fun onStart()
    fun onStop()
    fun toggle(): String?
}
