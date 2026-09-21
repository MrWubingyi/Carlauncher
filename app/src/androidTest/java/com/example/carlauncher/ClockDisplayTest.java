package com.example.carlauncher;

import android.text.format.DateFormat;
import android.widget.TextClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ClockDisplayTest {
    @Test
    public void clockMatchesSystemTime_afterLaunch() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(ClockDisplayTest::assertSystemTime);
        }
    }

    private static void assertSystemTime(MainActivity activity) {
        TextClock clock = activity.findViewById(R.id.timeText);
        assertNotNull(clock);
        CharSequence format = clock.is24HourModeEnabled()
                ? clock.getFormat24Hour() : clock.getFormat12Hour();
        long now = System.currentTimeMillis();
        String actual = clock.getText().toString();
        // The assertion may run across a minute boundary before the next UI tick.
        String current = DateFormat.format(format, new Date(now)).toString();
        String previous = DateFormat.format(format, new Date(now - 1000)).toString();
        assertTrue("Clock should follow the system: " + actual,
                actual.equals(current) || actual.equals(previous));
    }
}
