package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SlowMarqueePolicyTest {
    @Test public void identicalSnapshotsDoNotCountAsContentChanges() {
        assertFalse(SlowMarqueePolicy.contentChanged("A very long track title", "A very long track title"));
        assertFalse(SlowMarqueePolicy.contentChanged(null, ""));
        assertTrue(SlowMarqueePolicy.contentChanged("Track A", "Track B"));
    }

    @Test public void onlyVisibleOverflowingTextAnimates() {
        assertFalse(SlowMarqueePolicy.shouldAnimate(100, 120, true));
        assertFalse(SlowMarqueePolicy.shouldAnimate(200, 120, false));
        assertTrue(SlowMarqueePolicy.shouldAnimate(200, 120, true));
        assertTrue(SlowMarqueePolicy.overflowPx(200, 120) == 80);
    }
}
