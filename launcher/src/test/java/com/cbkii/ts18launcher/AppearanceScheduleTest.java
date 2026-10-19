package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AppearanceScheduleTest {
    @Test public void defaultsSwitchAtAnchorsWithNoTransition() {
        assertEquals("night", AppearanceSchedule.resolve(419, -1, -1));
        assertEquals("day", AppearanceSchedule.resolve(420, -1, -1));
        assertEquals("day", AppearanceSchedule.resolve(1139, -1, -1));
        assertEquals("night", AppearanceSchedule.resolve(1140, -1, -1));
    }
    @Test public void allMinutesResolveToExactlyTwoModes() {
        for (int minute = 0; minute < 1440; minute++) {
            String mode = AppearanceSchedule.resolve(minute, -1, -1);
            assertTrue("day".equals(mode) || "night".equals(mode));
        }
    }
    @Test public void overnightAndShortDayHaveExactInclusiveDawnExclusiveDusk() {
        assertEquals("day", AppearanceSchedule.resolve(1200, 1200, 360));
        assertEquals("day", AppearanceSchedule.resolve(359, 1200, 360));
        assertEquals("night", AppearanceSchedule.resolve(360, 1200, 360));
        assertEquals("day", AppearanceSchedule.resolve(421, 420, 422));
        assertEquals("night", AppearanceSchedule.resolve(422, 420, 422));
    }
    @Test public void equalAndPartialDefaultAnchorsAreValidated() {
        assertFalse(AppearanceSchedule.valid(420, 420));
        assertFalse(AppearanceSchedule.valid(-1, 420));
        assertFalse(AppearanceSchedule.valid(1140, -1));
        assertTrue(AppearanceSchedule.valid(420, 421));
        assertEquals("day", AppearanceSchedule.resolve(720, 420, 420));
        assertEquals("night", AppearanceSchedule.resolve(0, 420, 420));
    }
    @Test public void legacyModesCollapseWithoutLosingManualOverrides() {
        assertEquals("day", LauncherPrefs.canonicalAppearance("high_contrast"));
        assertEquals("night", LauncherPrefs.canonicalAppearance("dim"));
        assertEquals("night", LauncherPrefs.canonicalAppearance("night"));
        assertEquals("auto", LauncherPrefs.canonicalAppearance("unknown"));
    }
}
