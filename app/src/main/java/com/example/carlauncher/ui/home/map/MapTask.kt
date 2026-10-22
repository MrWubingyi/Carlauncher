package com.example.carlauncher.ui.home.map

import android.content.Context
import android.view.View

/**
 * Optional platform task adapter. Implementations must not retain an Activity or View.
 * A view belongs to one composition; releaseView must detach its task resources.
 * onStop pauses the task, close releases it when the ViewModel is cleared.
 * No embedded task is shipped: the default factory returns null for the demo map.
 */
interface MapTask {
    fun createView(context: Context): View
    fun releaseView(view: View)
    fun onStart()
    fun onStop()
    fun close()
}

fun interface MapTaskFactory {
    fun create(): MapTask?
}
