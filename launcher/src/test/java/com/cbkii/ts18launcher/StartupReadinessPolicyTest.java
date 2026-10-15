package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cbkii.ts18launcher.StartupReadinessPolicy.Mode;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Session;
import com.cbkii.ts18launcher.StartupReadinessPolicy.State;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Trigger;
import org.junit.Test;

public final class StartupReadinessPolicyTest {
    @Test public void bootWakeAndReturnAreDistinct() {
        assertEquals(Session.FULL_BOOT, StartupReadinessPolicy.classify(Trigger.HOME_RESUME, true));
        assertEquals(Session.WAKE, StartupReadinessPolicy.classify(Trigger.SCREEN_ON, false));
        assertEquals(Session.HOME_RETURN, StartupReadinessPolicy.classify(Trigger.HOME_RESUME, false));
        assertEquals(Session.HOME_RETURN, StartupReadinessPolicy.classify(Trigger.STORAGE_MOUNT, false));
    }

    @Test public void duplicateTriggersInsideDebounceAreDropped() {
        assertTrue(StartupReadinessPolicy.debounced(Trigger.HOME_RESUME, Trigger.HOME_RESUME, 2000L, 1000L));
        assertFalse(StartupReadinessPolicy.debounced(Trigger.HOME_RESUME, Trigger.HOME_RESUME, 2600L, 1000L));
        assertFalse(StartupReadinessPolicy.debounced(Trigger.SCREEN_ON, Trigger.HOME_RESUME, 1100L, 1000L));
        assertFalse(StartupReadinessPolicy.debounced(Trigger.HOME_RESUME, null, 1100L, 0L));
    }

    @Test public void oneInFlightAdmissionAndHealthySourcesUntouched() {
        assertFalse(StartupReadinessPolicy.shouldAdmit(State.STARTING));
        assertFalse(StartupReadinessPolicy.shouldAdmit(State.READY));
        assertFalse(StartupReadinessPolicy.shouldAdmit(State.ACTIVE));
        assertFalse(StartupReadinessPolicy.shouldAdmit(State.BLOCKED));
        assertFalse(StartupReadinessPolicy.shouldAdmit(State.UNAVAILABLE));
        assertTrue(StartupReadinessPolicy.shouldAdmit(State.UNCERTAIN));
        assertTrue(StartupReadinessPolicy.shouldAdmit(State.FAILED));
    }

    @Test public void foregroundStageOnlyAfterBackgroundFailureAndSafeGates() {
        assertFalse(StartupReadinessPolicy.mayStageForeground(Mode.BACKGROUND_ONLY, Session.FULL_BOOT,
                true, true, true, true, false));
        assertFalse(StartupReadinessPolicy.mayStageForeground(Mode.AUTOMATIC_FALLBACK, Session.FULL_BOOT,
                false, true, true, true, false));
        assertTrue(StartupReadinessPolicy.mayStageForeground(Mode.AUTOMATIC_FALLBACK, Session.FULL_BOOT,
                true, true, true, true, false));
        assertTrue(StartupReadinessPolicy.mayStageForeground(Mode.AUTOMATIC_FALLBACK, Session.WAKE,
                true, true, true, true, false));
        assertFalse(StartupReadinessPolicy.mayStageForeground(Mode.AUTOMATIC_FALLBACK, Session.HOME_RETURN,
                true, true, true, true, false));
        assertTrue(StartupReadinessPolicy.mayStageForeground(Mode.AGGRESSIVE, Session.HOME_RETURN,
                true, true, true, true, false));
        // HOME not in front, screen off, audible audio/call, or already staged: never stage.
        assertFalse(StartupReadinessPolicy.mayStageForeground(Mode.AGGRESSIVE, Session.FULL_BOOT,
                true, false, true, true, false));
        assertFalse(StartupReadinessPolicy.mayStageForeground(Mode.AGGRESSIVE, Session.FULL_BOOT,
                true, true, false, true, false));
        assertFalse(StartupReadinessPolicy.mayStageForeground(Mode.AGGRESSIVE, Session.FULL_BOOT,
                true, true, true, false, false));
        assertFalse(StartupReadinessPolicy.mayStageForeground(Mode.AGGRESSIVE, Session.FULL_BOOT,
                true, true, true, true, true));
    }

    @Test public void homeRestorationYieldsWhenCancelledOrOwnershipDiffers() {
        assertTrue(StartupReadinessPolicy.shouldRestoreHome(true, false, true));
        assertFalse(StartupReadinessPolicy.shouldRestoreHome(false, false, true));
        assertFalse(StartupReadinessPolicy.shouldRestoreHome(true, true, true));
        assertFalse(StartupReadinessPolicy.shouldRestoreHome(true, false, false));
    }

    @Test public void modeParsingDefaultsToAutomaticFallback() {
        assertEquals(Mode.BACKGROUND_ONLY, StartupReadinessPolicy.mode("background"));
        assertEquals(Mode.AGGRESSIVE, StartupReadinessPolicy.mode("aggressive"));
        assertEquals(Mode.AUTOMATIC_FALLBACK, StartupReadinessPolicy.mode("auto"));
        assertEquals(Mode.AUTOMATIC_FALLBACK, StartupReadinessPolicy.mode(null));
    }

    @Test public void navRadioDeadlineOutlastsColdPlayGuardPlusAck() {
        long timeout = MediaPreparationPolicy.commandTimeoutMs(MediaSourceAdapter.NAVRADIO_PACKAGE, 4500L);
        assertTrue(timeout >= MediaPreparationPolicy.NAVRADIO_COLD_PLAY_NOT_BEFORE_MS
                + MediaPreparationPolicy.NAVRADIO_ADMISSION_MS + MediaPreparationPolicy.ACK_WINDOW_MS);
        assertEquals(4500L, MediaPreparationPolicy.commandTimeoutMs(MediaSourceAdapter.AUXIO_PACKAGE, 4500L));
    }
}
