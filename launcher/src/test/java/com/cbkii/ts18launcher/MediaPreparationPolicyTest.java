package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaPreparationPolicyTest {
    @Test public void rootSuccessDoesNotDuplicateNormalServiceStart() {
        assertEquals(MediaPreparationPolicy.NextRoute.ROOT_ACCEPTED,
                MediaPreparationPolicy.afterRoot(MediaPreparationPolicy.RootOutcome.SUCCESS, true));
    }

    @Test public void rootDeniedTimeoutAndUnavailableUseNormalFallbackWhenPresent() {
        for (MediaPreparationPolicy.RootOutcome outcome : new MediaPreparationPolicy.RootOutcome[] {
                MediaPreparationPolicy.RootOutcome.DENIED_OR_FAILED,
                MediaPreparationPolicy.RootOutcome.TIMEOUT,
                MediaPreparationPolicy.RootOutcome.UNAVAILABLE}) {
            assertEquals(MediaPreparationPolicy.NextRoute.NORMAL_FALLBACK,
                    MediaPreparationPolicy.afterRoot(outcome, true));
        }
    }

    @Test public void bothActivationRoutesUnavailableFailClosed() {
        assertEquals(MediaPreparationPolicy.NextRoute.FAIL,
                MediaPreparationPolicy.afterRoot(
                        MediaPreparationPolicy.RootOutcome.UNAVAILABLE, false));
    }

    @Test public void lateOrCancelledGenerationCannotDispatch() {
        assertTrue(MediaPreparationPolicy.generationActive(false, 3, 3, 1000L, 2000L));
        assertFalse(MediaPreparationPolicy.generationActive(true, 3, 3, 1000L, 2000L));
        assertFalse(MediaPreparationPolicy.generationActive(false, 3, 4, 1000L, 2000L));
        assertFalse(MediaPreparationPolicy.generationActive(false, 3, 3, 2000L, 2000L));
        assertFalse(MediaPreparationPolicy.shouldDispatch(false, false, false, true));
    }

    @Test public void aReadyTransactionDispatchesOnceAndLateCallbacksCannotReplayIt() {
        assertTrue(MediaPreparationPolicy.shouldDispatch(false, false, true, true));
        assertFalse(MediaPreparationPolicy.shouldDispatch(false, true, true, true));
        assertFalse(MediaPreparationPolicy.shouldDispatch(true, false, true, true));
        assertFalse(MediaPreparationPolicy.shouldDispatch(false, false, true, false));
    }

    @Test public void duplicateColdPlayIsCoalescedBeforeDispatchOnly() {
        assertTrue(MediaPreparationPolicy.shouldCoalesce(
                MediaCommandPolicy.Desired.PLAY, MediaCommandPolicy.Desired.PLAY, false, false));
        assertFalse(MediaPreparationPolicy.shouldCoalesce(
                MediaCommandPolicy.Desired.PLAY, MediaCommandPolicy.Desired.PAUSE, false, false));
        assertFalse(MediaPreparationPolicy.shouldCoalesce(
                MediaCommandPolicy.Desired.PLAY, MediaCommandPolicy.Desired.PLAY, false, true));
        assertFalse(MediaPreparationPolicy.shouldCoalesce(
                MediaCommandPolicy.Desired.PLAY, MediaCommandPolicy.Desired.PLAY, true, false));
    }

    @Test public void oppositeSourceIsPausedOnlyAfterAcknowledgedPlay() {
        assertFalse(MediaPreparationPolicy.shouldCommitOppositePause(false, true, false));
        assertFalse(MediaPreparationPolicy.shouldCommitOppositePause(true, false, false));
        assertFalse(MediaPreparationPolicy.shouldCommitOppositePause(true, true, true));
        assertTrue(MediaPreparationPolicy.shouldCommitOppositePause(true, true, false));
    }
}
