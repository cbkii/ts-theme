package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MediaPreparationPolicyTest {
    @Test public void rootSuccessWinsWithoutNormalFallback() {
        assertEquals(MediaPreparationPolicy.StartRoute.ROOT,
                MediaPreparationPolicy.resolveStartRoute(true, false));
        assertEquals(MediaPreparationPolicy.StartRoute.ROOT,
                MediaPreparationPolicy.resolveStartRoute(true, true));
    }

    @Test public void deniedOrUnavailableRootFallsBackToNormalAndroid() {
        assertEquals(MediaPreparationPolicy.StartRoute.NORMAL_ANDROID,
                MediaPreparationPolicy.resolveStartRoute(false, true));
        assertEquals(MediaPreparationPolicy.StartRoute.FAILED,
                MediaPreparationPolicy.resolveStartRoute(false, false));
    }

    @Test public void staleGenerationOrExpiredResultIsRejected() {
        assertTrue(MediaPreparationPolicy.callbackIsCurrent(false, 2, 2, 99L, 100L));
        assertFalse(MediaPreparationPolicy.callbackIsCurrent(true, 2, 2, 99L, 100L));
        assertFalse(MediaPreparationPolicy.callbackIsCurrent(false, 2, 3, 99L, 100L));
        assertFalse(MediaPreparationPolicy.callbackIsCurrent(false, 2, 2, 100L, 100L));
    }

    @Test public void oppositeSourcePauseRequiresAcknowledgedCommitPreconditions() {
        assertTrue(MediaPreparationPolicy.shouldCommitOppositePause(
                "music", "radio", true, true));
        assertFalse(MediaPreparationPolicy.shouldCommitOppositePause(
                "music", "radio", false, true));
        assertFalse(MediaPreparationPolicy.shouldCommitOppositePause(
                "music", "radio", true, false));
        assertFalse(MediaPreparationPolicy.shouldCommitOppositePause(
                "music", "music", true, true));
    }
}
