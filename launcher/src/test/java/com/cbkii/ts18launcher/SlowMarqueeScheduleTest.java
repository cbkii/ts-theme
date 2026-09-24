package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SlowMarqueeScheduleTest {
    @Test public void configuredHoldIsFiveSeconds() {
        assertEquals(5000L, SlowMarqueeSchedule.HOLD_MS);
        assertEquals(SlowMarqueeSchedule.HOLD_MS, SlowMarqueeTextView.HOLD_MS);
    }

    @Test public void aNewScheduleInvalidatesAnOlderDelayedCallback() {
        SlowMarqueeSchedule schedule = new SlowMarqueeSchedule();
        int oldToken = schedule.arm();
        int newToken = schedule.arm();
        assertFalse(schedule.accepts(oldToken));
        assertTrue(schedule.accepts(newToken));
    }

    @Test public void hideOrDetachCancelsDelayedAnimationWork() {
        SlowMarqueeSchedule schedule = new SlowMarqueeSchedule();
        int token = schedule.arm();
        schedule.cancel();
        assertFalse(schedule.accepts(token));
    }

    @Test public void reattachCreatesExactlyOneCurrentGeneration() {
        SlowMarqueeSchedule schedule = new SlowMarqueeSchedule();
        int first = schedule.arm();
        schedule.cancel();
        int second = schedule.arm();
        assertFalse(schedule.accepts(first));
        assertTrue(schedule.accepts(second));
    }
}
