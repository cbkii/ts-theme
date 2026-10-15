package com.cbkii.ts18launcher;

import static org.junit.Assert.*;
import org.junit.Test;
import static com.cbkii.ts18launcher.StartupReadinessPolicy.*;

public class StartupReadinessPolicyTest {
    @Test public void classifiesBootWakeAndReturn() {
        assertEquals(Kind.FULL_BOOT, classify(5, 4, false));
        assertEquals(Kind.FULL_BOOT, classify(5, -2, true));
        assertEquals(Kind.SAME_BOOT_WAKE, classify(5, 5, true));
        assertEquals(Kind.HOME_RETURN, classify(5, 5, false));
    }

    @Test public void healthySourcesAreNotReadmittedAndOneInFlight() {
        assertFalse(admit(State.READY, false));
        assertFalse(admit(State.ACTIVE, false));
        assertFalse(admit(State.STARTING, true));
        assertFalse(admit(State.BLOCKED, false));
        assertTrue(admit(State.UNAVAILABLE, false));
        assertTrue(admit(State.FAILED, false));
    }

    @Test public void homeReturnWithHealthySourcesDoesNothing() {
        assertFalse(shouldReconcile(Kind.HOME_RETURN, State.READY, State.ACTIVE));
        assertTrue(shouldReconcile(Kind.HOME_RETURN, State.READY, State.FAILED));
        assertTrue(shouldReconcile(Kind.FULL_BOOT, State.READY, State.READY));
    }

    @Test public void sessionStateDistinguishesPlayAdvertisement() {
        assertEquals(State.UNAVAILABLE, stateOf(false, false, 0));
        assertEquals(State.UNCERTAIN, stateOf(true, false, 0));
        assertEquals(State.READY, stateOf(true, false, android.media.session.PlaybackState.ACTION_PLAY));
        assertEquals(State.ACTIVE, stateOf(true, true, 0));
    }

    @Test public void foregroundGatedByModeAndSafety() {
        assertFalse(foregroundAllowed(Mode.BACKGROUND, true, true, false, false, true));
        assertTrue(foregroundAllowed(Mode.AUTOMATIC, true, true, false, false, true));
        assertFalse(foregroundAllowed(Mode.AUTOMATIC, false, true, false, false, true));
        assertFalse(foregroundAllowed(Mode.AUTOMATIC, true, false, false, false, true));
        assertFalse(foregroundAllowed(Mode.AUTOMATIC, true, true, true, false, true));
        assertFalse(foregroundAllowed(Mode.AUTOMATIC, true, true, false, true, true));
        assertFalse(foregroundAllowed(Mode.AGGRESSIVE, true, true, false, false, false));
        assertEquals(0L, backgroundWindowMs(Mode.AGGRESSIVE));
        assertEquals(Mode.AUTOMATIC, mode("bogus"));
    }

    @Test public void coldPlayDeadlineGivesFullAckWindowAfterGuard() {
        long deadline = 4500L, notBefore = 4200L;
        assertEquals(notBefore + MediaSourceBootstrapper.ACK_TIMEOUT_MS,
                MediaSourceBootstrapper.coldPlayDeadline(deadline, notBefore));
        assertEquals(9000L, MediaSourceBootstrapper.coldPlayDeadline(9000L, 3000L));
    }
}
