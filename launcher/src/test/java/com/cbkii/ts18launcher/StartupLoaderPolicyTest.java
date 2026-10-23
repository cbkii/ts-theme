package com.cbkii.ts18launcher;

import static org.junit.Assert.*;
import org.junit.Test;

public class StartupLoaderPolicyTest {
    @Test public void optInIsRequired() {
        assertFalse(StartupLoaderPolicy.shouldStart(false, true, 5, -1, true));
    }
    @Test public void newAndroidBootStartsOnce() {
        assertTrue(StartupLoaderPolicy.shouldStart(true, false, 6, 5, false));
        assertFalse(StartupLoaderPolicy.shouldStart(true, false, 6, 6, false));
    }
    @Test public void sleepOptionIsIndependent() {
        assertFalse(StartupLoaderPolicy.shouldStart(true, false, 6, 6, true));
        assertTrue(StartupLoaderPolicy.shouldStart(true, true, 6, 6, true));
        assertFalse(StartupLoaderPolicy.shouldStart(true, true, 6, 6, false));
    }
    @Test public void unknownBootDoesNotInventColdBoot() {
        assertFalse(StartupLoaderPolicy.shouldStart(true, true, -1, 6, true));
    }
    @Test public void duplicateBeginCannotExtendDeadline() {
        StartupLoaderPolicy p = new StartupLoaderPolicy();
        assertTrue(p.begin(100));
        assertFalse(p.begin(1000));
        assertFalse(p.expired(8099));
        assertTrue(p.expired(8100));
    }
    @Test public void dismissalDoesNotRemainActive() {
        StartupLoaderPolicy p = new StartupLoaderPolicy();
        p.begin(100); p.finish();
        assertFalse(p.active());
        assertFalse(p.expired(50000));
    }
    @Test public void unresolvedWorkCannotProveCompletion() {
        assertFalse(StartupLoaderPolicy.terminal(StartupReadinessPolicy.State.STARTING));
        assertFalse(StartupLoaderPolicy.terminal(StartupReadinessPolicy.State.UNAVAILABLE));
        assertTrue(StartupLoaderPolicy.terminal(StartupReadinessPolicy.State.BLOCKED));
        assertTrue(StartupLoaderPolicy.terminal(StartupReadinessPolicy.State.UNCERTAIN));
    }
}
