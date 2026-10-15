package com.cbkii.ts18launcher;

import static com.cbkii.ts18launcher.StartupReadinessPolicy.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class StartupReadinessPolicyTest {
    @Test public void bootWakeAndHomeReturnAreDistinct() {
        assertEquals(Kind.FULL_BOOT, classify(Trigger.BOOT, 5, 5));
        assertEquals(Kind.FULL_BOOT, classify(Trigger.HOME_RESUME, 6, 5));
        assertEquals(Kind.SAME_BOOT_WAKE, classify(Trigger.SCREEN_ON, 5, 5));
        assertEquals(Kind.HOME_RETURN, classify(Trigger.HOME_RESUME, 5, 5));
    }

    @Test public void overlappingTriggersAreDeduplicated() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        assertTrue(p.acceptTrigger(Trigger.HOME_RESUME, 1000));
        assertFalse(p.acceptTrigger(Trigger.HOME_RESUME, 1400));
        assertTrue(p.acceptTrigger(Trigger.SCREEN_ON, 1400));
        assertTrue(p.acceptTrigger(Trigger.HOME_RESUME, 3000));
    }

    @Test public void oneInFlightAdmissionPerSource() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        assertTrue(p.admit(Source.MUSIC, Trigger.BOOT, 0));
        p.begin(Source.MUSIC, Trigger.BOOT, Method.BROWSER, 0);
        assertFalse(p.admit(Source.MUSIC, Trigger.HOME_RESUME, 10));
        assertFalse(p.admit(Source.MUSIC, Trigger.SESSION_LOST, 10));
        assertTrue(p.admit(Source.RADIO, Trigger.BOOT, 10));
    }

    @Test public void healthySourceIsNotDisturbedButSessionLossAndLateUsbRecover() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        int g = p.begin(Source.MUSIC, Trigger.BOOT, Method.BROWSER, 0);
        assertTrue(p.ready(Source.MUSIC, g, 900));
        assertFalse(p.admit(Source.MUSIC, Trigger.HOME_RESUME, 1000));
        assertFalse(p.admit(Source.MUSIC, Trigger.SCREEN_ON, 1000));
        assertTrue(p.admit(Source.MUSIC, Trigger.STORAGE_MOUNT, 1000));
        assertTrue(p.admit(Source.MUSIC, Trigger.SESSION_LOST, 1000));
    }

    @Test public void staleGenerationResultIsIgnored() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        int first = p.begin(Source.RADIO, Trigger.BOOT, Method.SERVICE, 0);
        assertTrue(p.fail(Source.RADIO, first, "admission", 100));
        int second = p.begin(Source.RADIO, Trigger.SCREEN_ON, Method.ACTIVITY_BOOTSTRAP, 20000);
        assertFalse(p.ready(Source.RADIO, first, 20100));
        assertEquals(State.STARTING, p.record(Source.RADIO).state);
        assertTrue(p.ready(Source.RADIO, second, 20200));
        assertEquals("", p.record(Source.RADIO).firstFailingBoundary);
    }

    @Test public void firstFailingBoundaryIsKeptAndRetriesAreBounded() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        long now = 0;
        for (int i = 0; i < MAX_ATTEMPTS_PER_BOOT; i++) {
            assertTrue(p.admit(Source.RADIO, Trigger.SCREEN_ON, now));
            int g = p.begin(Source.RADIO, Trigger.SCREEN_ON, Method.SERVICE, now);
            assertTrue(p.fail(Source.RADIO, g, "session", now + 100));
            now += FAILED_RETRY_SPACING_MS + 1000;
        }
        assertFalse(p.admit(Source.RADIO, Trigger.SCREEN_ON, now));
        p.resetForBoot();
        assertTrue(p.admit(Source.RADIO, Trigger.BOOT, now));
    }

    @Test public void failedSourceWaitsForRetrySpacing() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        int g = p.begin(Source.NAVIGATION, Trigger.BOOT, Method.ACTIVITY_BOOTSTRAP, 0);
        p.fail(Source.NAVIGATION, g, "splash", 100);
        assertFalse(p.admit(Source.NAVIGATION, Trigger.HOME_RESUME, 5000));
        assertTrue(p.admit(Source.NAVIGATION, Trigger.HOME_RESUME, 20000));
    }

    @Test public void uncertainActivityLaunchIsNeverRepeated() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        int g = p.begin(Source.NAVIGATION, Trigger.BOOT, Method.ACTIVITY_BOOTSTRAP, 0);
        assertTrue(p.markActivityLaunch(Source.NAVIGATION));
        assertTrue(p.uncertain(Source.NAVIGATION, g, "task-probe", 50));
        assertFalse(p.admit(Source.NAVIGATION, Trigger.HOME_RESUME, 90000));
        assertFalse(p.markActivityLaunch(Source.NAVIGATION));
    }

    @Test public void modesGateForegroundBootstrap() {
        assertFalse(foregroundAllowed(parseMode("background")));
        assertTrue(foregroundAllowed(parseMode("auto")));
        assertTrue(foregroundAllowed(parseMode("aggressive")));
        assertEquals(Mode.AUTOMATIC_FALLBACK, parseMode("garbage"));
    }

    @Test public void lateUsbMountBypassesFailureSpacingAndRetryCap() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        int g = 0;
        for (int i = 0; i < MAX_ATTEMPTS_PER_BOOT; i++) {
            g = p.begin(Source.MUSIC, Trigger.BOOT, Method.BROWSER, i * 20000L);
            p.fail(Source.MUSIC, g, "no-queue", i * 20000L + 100);
        }
        assertFalse(p.admit(Source.MUSIC, Trigger.HOME_RESUME, 70000));
        assertTrue(p.admit(Source.MUSIC, Trigger.STORAGE_MOUNT, 60200));
    }

    @Test public void taskAdoptionAfterUncertainLaunchBecomesReady() {
        StartupReadinessPolicy p = new StartupReadinessPolicy();
        int g = p.begin(Source.NAVIGATION, Trigger.BOOT, Method.ACTIVITY_BOOTSTRAP, 0);
        p.uncertain(Source.NAVIGATION, g, "awaiting-task-adoption", 100);
        assertTrue(p.observedReady(Source.NAVIGATION, 200));
        assertEquals(State.READY, p.record(Source.NAVIGATION).state);
        assertFalse(p.observedReady(Source.NAVIGATION, 300));
    }
}
