package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaTickerPolicyTest {
    @Test public void identicalPrimarySnapshotsDoNotRestartTicker() {
        assertFalse(MediaTickerPolicy.primaryChanged("Track", "Track"));
        assertFalse(MediaTickerPolicy.primaryChanged(" Track ", "Track"));
    }

    @Test public void secondaryOnlyChangeDoesNotImplyPrimaryRestart() {
        assertFalse(MediaTickerPolicy.primaryChanged("Track", "Track"));
        assertTrue(MediaTickerPolicy.secondaryChanged("Artist A", "Artist B"));
    }

    @Test public void primaryOrWidthChangesRestartTicker() {
        assertTrue(MediaTickerPolicy.primaryChanged("Track A", "Track B"));
        assertTrue(MediaTickerPolicy.widthChangeRestarts(400, 380));
        assertFalse(MediaTickerPolicy.widthChangeRestarts(400, 400));
    }

    @Test public void onlyOverflowingVisibleTextScrolls() {
        assertTrue(MediaTickerPolicy.shouldScroll(501, 500, true));
        assertFalse(MediaTickerPolicy.shouldScroll(500, 500, true));
        assertFalse(MediaTickerPolicy.shouldScroll(501, 500, false));
    }

    @Test public void hiddenOrDetachedViewsDoNotScheduleDelayedAnimation() {
        assertTrue(MediaTickerPolicy.shouldSchedule(true, true, true));
        assertFalse(MediaTickerPolicy.shouldSchedule(false, true, true));
        assertFalse(MediaTickerPolicy.shouldSchedule(true, false, true));
        assertFalse(MediaTickerPolicy.shouldSchedule(true, true, false));
    }
}
