package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** TS18 capture 2026-11-14: bounded Maps evidence polling without a 500 ms consent loop. */
public class CompactStartupPollPolicyTest {
    @Test public void servingPeerWithoutRendererIsPolledAndStopsOnceReady() {
        assertEquals(500L, CompactStartupPollPolicy.delayMs(true, false, false, 0));
        assertEquals(-1L, CompactStartupPollPolicy.delayMs(true, false, true, 0));
    }

    @Test public void consentOnlyAnswersBackOffInsteadOfPollingEvery500Ms() {
        assertEquals(500L, CompactStartupPollPolicy.delayMs(true, true, false, 1));
        assertEquals(1000L, CompactStartupPollPolicy.delayMs(true, true, false, 2));
        assertEquals(1500L, CompactStartupPollPolicy.delayMs(true, true, false, 3));
        assertEquals(1500L, CompactStartupPollPolicy.delayMs(true, true, false, 12));
    }

    @Test public void absentPeerKeepsItsExistingThreeSecondCap() {
        assertEquals(500L, CompactStartupPollPolicy.delayMs(false, false, false, 1));
        assertEquals(3000L, CompactStartupPollPolicy.delayMs(false, false, false, 9));
        assertEquals(-1L, CompactStartupPollPolicy.delayMs(false, false, false, 0));
    }
}
