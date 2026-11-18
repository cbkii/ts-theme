package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class StartupTimelineTest {
    private long now;
    private StartupTimeline.Clock original;

    @Before public void setUp() {
        original = StartupTimeline.clock;
        StartupTimeline.resetForTest();
        now = 20_000L;
        StartupTimeline.clock = new StartupTimeline.Clock() {
            @Override public long elapsed() { return now; }
            @Override public long processStart() { return 19_400L; }
        };
    }

    @After public void tearDown() {
        StartupTimeline.clock = original;
        StartupTimeline.resetForTest();
    }

    @Test public void phasesAreRecordedOncePerProcessRelativeToProcessStart() {
        assertTrue(StartupTimeline.mark("home.create"));
        now += 150L;
        assertTrue(StartupTimeline.mark("home.first_draw"));
        now += 900L;
        assertFalse(StartupTimeline.mark("home.first_draw"));
        assertEquals(600L, StartupTimeline.offset("home.create"));
        assertEquals(750L, StartupTimeline.offset("home.first_draw"));
        assertEquals(-1L, StartupTimeline.offset("music.ready"));
        assertTrue(StartupTimeline.dump().contains("+750ms home.first_draw"));
    }

    @Test public void spanTimesTheStepAndPropagatesItsFailure() {
        StartupTimeline.span("ok", () -> now += 70L);
        assertEquals(20_070L, now);
        try {
            StartupTimeline.span("fails", () -> { throw new IllegalStateException("boom"); });
            throw new AssertionError("expected propagation");
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }
    }

    @Test public void phaseCountIsBounded() {
        for (int i = 0; i < 100; i++) StartupTimeline.mark("p" + i);
        assertEquals(-1L, StartupTimeline.offset("p99"));
        assertTrue(StartupTimeline.offset("p0") >= 0L);
    }
}
