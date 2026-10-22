package com.example.carlauncher.ui.home.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** The Activity supplies this slot; the task supplies the View. */
@Composable
fun MapTaskContainer(task: MapTask, modifier: Modifier = Modifier) {
    key(task) {
        AndroidView(
            factory = task::createView,
            modifier = modifier,
            onRelease = task::releaseView,
        )
    }
}
