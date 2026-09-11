package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppearanceScheduleTest {
    @Test public void genericDefaultsUseDimAroundTransitions() {
        assertEquals(LauncherPrefs.APPEARANCE_DIM, AppearanceSchedule.resolve(6 * 60 + 30, -1, -1));
        assertEquals(LauncherPrefs.APPEARANCE_DIM, AppearanceSchedule.resolve(19 * 60 + 20, -1, -1));
    }

    @Test public void genericDefaultsUseHighContrastThroughBrightMiddayWindow() {
        assertEquals(LauncherPrefs.APPEARANCE_HIGH_CONTRAST,
                AppearanceSchedule.resolve(12 * 60, -1, -1));
    }

    @Test public void genericDefaultsUseNightOutsideDayWindow() {
        assertEquals(LauncherPrefs.APPEARANCE_NIGHT,
                AppearanceSchedule.resolve(23 * 60, -1, -1));
    }

    @Test public void configuredScheduleHandlesWindowAcrossMidnight() {
        assertEquals(LauncherPrefs.APPEARANCE_HIGH_CONTRAST,
                AppearanceSchedule.resolve(23 * 60, 20 * 60, 6 * 60));
        assertEquals(LauncherPrefs.APPEARANCE_NIGHT,
                AppearanceSchedule.resolve(12 * 60, 20 * 60, 6 * 60));
    }
}
