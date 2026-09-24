package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarqueePolicyTest {
    @Test public void unchangedPrimaryDoesNotRestartSemanticHold() {
        assertFalse(MarqueePolicy.primaryChanged("Station A", "Station A"));
        assertTrue(MarqueePolicy.primaryChanged("Station A", "Station B"));
    }

    @Test public void textThatFitsDoesNotAnimate() {
        assertFalse(MarqueePolicy.shouldAnimate(120, 120));
        assertFalse(MarqueePolicy.shouldAnimate(80, 120));
        assertTrue(MarqueePolicy.shouldAnimate(121, 120));
        assertEquals(1, MarqueePolicy.overflowPx(121, 120));
    }

    @Test public void longTextGetsBoundedLowSpeedDuration() {
        assertEquals(MarqueePolicy.MIN_DURATION_MS, MarqueePolicy.durationMs(1, 1f));
        assertEquals(MarqueePolicy.MAX_DURATION_MS, MarqueePolicy.durationMs(5000, 1f));
        assertEquals(5000L, MarqueePolicy.HOLD_MS);
    }
}
