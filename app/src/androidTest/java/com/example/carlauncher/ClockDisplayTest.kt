package com.example.carlauncher

import android.text.format.DateFormat
import android.widget.TextClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Date
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class ClockDisplayTest {
    @Test
    open fun clockMatchesSystemTime_afterLaunch() {
        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)!!.use { scenario ->
            scenario!!.onActivity(
                ActivityScenario.ActivityAction<MainActivity?> { assertSystemTime(it) }
            )
        }
    }

    private fun assertSystemTime(activity: MainActivity?) {
        val clock = activity!!.findViewById<TextClock?>(R.id.timeText)
        assertNotNull(clock)
        val format =
            if (clock!!.is24HourModeEnabled()) clock!!.getFormat24Hour()
            else clock!!.getFormat12Hour()
        val now = System.currentTimeMillis()
        val actual = clock!!.getText().toString()
        // The assertion may run across a minute boundary before the next UI tick.
        val current = DateFormat.format(format, Date(now)).toString()
        val previous = DateFormat.format(format, Date(now - 1000)).toString()
        assertTrue(
            "Clock should follow the system: " + actual,
            actual == current || actual == previous,
        )
    }
}
