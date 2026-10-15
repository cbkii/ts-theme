package com.cbkii.ts18launcher;

import static org.junit.Assert.*;

import com.cbkii.ts18launcher.StartupReadinessPolicy.Mode;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Source;
import com.cbkii.ts18launcher.StartupReadinessPolicy.State;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Trigger;
import org.junit.Test;

public class StartupReadinessPolicyTest {
    @Test public void bootCountDistinguishesFullBootFromRestart() {
        assertEquals(Trigger.BOOT, StartupReadinessPolicy.classifyProcessStart(4, 5));
        assertEquals(Trigger.BOOT, StartupReadinessPolicy.classifyProcessStart(-1, 5));
        assertEquals(Trigger.RESTART, StartupReadinessPolicy.classifyProcessStart(5, 5));
    }

    @Test public void overlappingTriggersAreDeduplicatedUnlessStronger() {
        assertTrue(StartupReadinessPolicy.shouldReconcile(Trigger.BOOT, 1000, 0, null));
        assertFalse(StartupReadinessPolicy.shouldReconcile(Trigger.RESUME, 1500, 1000, Trigger.BOOT));
        assertFalse(StartupReadinessPolicy.shouldReconcile(Trigger.WAKE, 1500, 1000, Trigger.WAKE));
        assertTrue(StartupReadinessPolicy.shouldReconcile(Trigger.SESSION_LOST, 1500, 1000, Trigger.RESUME));
        assertTrue(StartupReadinessPolicy.shouldReconcile(Trigger.RESUME, 5000, 1000, Trigger.BOOT));
    }

    @Test public void healthySourcesAreNeverReadmittedAndOneAdmissionIsInFlight() {
        for (Trigger t : Trigger.values()) {
            assertFalse(StartupReadinessPolicy.needsAdmission(State.READY, t, 100000, 0));
            assertFalse(StartupReadinessPolicy.needsAdmission(State.ACTIVE, t, 100000, 0));
            assertFalse(StartupReadinessPolicy.needsAdmission(State.STARTING, t, 100000, 0));
        }
        Source s = new Source("com.tw.media");
        assertTrue(s.begin("background", 10, true));
        assertFalse(s.begin("background", 20, true));
        assertEquals(1, s.generation);
    }

    @Test public void failedSourceRecoversOnUsbMountButResumeIsBackedOff() {
        assertTrue(StartupReadinessPolicy.needsAdmission(State.FAILED, Trigger.USB_MOUNT, 1000, 900));
        assertFalse(StartupReadinessPolicy.needsAdmission(State.FAILED, Trigger.RESUME, 1000, 900));
        assertTrue(StartupReadinessPolicy.needsAdmission(State.FAILED, Trigger.RESUME, 40000, 900));
    }

    @Test public void sessionLossDemotesOnlyHealthyStates() {
        assertEquals(State.UNCERTAIN, StartupReadinessPolicy.onSessionLost(State.READY));
        assertEquals(State.FAILED, StartupReadinessPolicy.onSessionLost(State.FAILED));
    }

    @Test public void firstFailingBoundaryIsKeptAndElapsedTracked() {
        Source s = new Source("com.navimods.radio");
        s.begin("background", 100, true);
        s.fail(State.FAILED, "service-admission", 600);
        s.fail(State.FAILED, "later", 900);
        assertEquals("service-admission", s.firstFailingBoundary);
        assertEquals(800, s.elapsedMs(0));
        s.begin("foreground-activity", 1000, false);
        assertEquals("", s.firstFailingBoundary);
        s.ready(1500);
        assertEquals(State.READY, s.state);
    }

    @Test public void foregroundBootstrapOnlyAsBoundedFallback() {
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.BACKGROUND, false, State.FAILED, false, true, false, false, false));
        assertTrue(StartupReadinessPolicy.foregroundAllowed(Mode.AUTOMATIC, false, State.FAILED, false, true, false, false, false));
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.AUTOMATIC, true, State.FAILED, false, true, false, false, false));
        assertTrue(StartupReadinessPolicy.foregroundAllowed(Mode.AGGRESSIVE, true, State.UNCERTAIN, false, true, false, false, false));
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.AGGRESSIVE, false, State.READY, false, true, false, false, false));
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.AGGRESSIVE, false, State.FAILED, true, true, false, false, false));
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.AGGRESSIVE, false, State.FAILED, false, false, false, false, false));
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.AGGRESSIVE, false, State.FAILED, false, true, true, false, false));
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.AGGRESSIVE, false, State.FAILED, false, true, false, true, false));
        assertFalse(StartupReadinessPolicy.foregroundAllowed(Mode.AGGRESSIVE, false, State.FAILED, false, true, false, false, true));
    }

    @Test public void loaderNeedsAggressiveModeAndOverlayAuthority() {
        assertFalse(StartupReadinessPolicy.loaderAllowed(Mode.AGGRESSIVE, false));
        assertFalse(StartupReadinessPolicy.loaderAllowed(Mode.AUTOMATIC, true));
        assertTrue(StartupReadinessPolicy.loaderAllowed(Mode.AGGRESSIVE, true));
        assertEquals(Mode.AUTOMATIC, Mode.parse("garbage"));
    }

    @Test public void navRadioDeadlinesSeparateStartupTransportAndAck() {
        assertEquals(MediaSourceBootstrapper.COMMAND_TIMEOUT_MS,
                MediaSourceBootstrapper.commandTimeoutMs("com.tw.media"));
        long deadline = MediaSourceBootstrapper.commandTimeoutMs("com.navimods.radio");
        assertTrue(deadline >= 3200L + MediaSourceBootstrapper.COMMAND_TIMEOUT_MS);
        // Guard is never skipped even when the service started just before the deadline.
        assertEquals(1000L + 3200L, MediaSourceBootstrapper.coldPlayNotBeforeMs(1000L, deadline));
        assertEquals(499L, MediaSourceBootstrapper.coldPlayNotBeforeMs(400L, 500L));
        assertEquals(0L, MediaSourceBootstrapper.coldPlayNotBeforeMs(0L, deadline));
    }
}
