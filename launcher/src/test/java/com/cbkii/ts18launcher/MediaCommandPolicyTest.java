package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import org.junit.Test;

public class MediaCommandPolicyTest {
    @Test public void playPauseResolvesOnceAtTapTime() {
        assertEquals(MediaCommandPolicy.Desired.PLAY,
                MediaCommandPolicy.resolve(MediaListenerService.Command.PLAY_PAUSE, false));
        assertEquals(MediaCommandPolicy.Desired.PAUSE,
                MediaCommandPolicy.resolve(MediaListenerService.Command.PLAY_PAUSE, true));
    }

    @Test public void advertisedActionsGateDispatch() {
        long playPause = PlaybackState.ACTION_PLAY_PAUSE;
        assertTrue(MediaCommandPolicy.supports(MediaCommandPolicy.Desired.PLAY, playPause));
        assertTrue(MediaCommandPolicy.supports(MediaCommandPolicy.Desired.PAUSE, playPause));
        assertFalse(MediaCommandPolicy.supports(MediaCommandPolicy.Desired.NEXT, playPause));
        assertTrue(MediaCommandPolicy.supports(MediaCommandPolicy.Desired.NEXT,
                PlaybackState.ACTION_SKIP_TO_NEXT));
    }

    @Test public void acknowledgementIsDirectionSpecific() {
        assertTrue(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.PLAY,
                PlaybackState.STATE_PLAYING));
        assertFalse(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.PLAY,
                PlaybackState.STATE_PAUSED));
        assertTrue(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.PAUSE,
                PlaybackState.STATE_PAUSED));
        assertFalse(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.PAUSE,
                PlaybackState.STATE_PLAYING));
    }

    @Test public void readinessDoesNotEquateAnySessionWithPlayable() {
        assertEquals(MediaCommandPolicy.Phase.CONNECTED,
                MediaCommandPolicy.phaseForController(PlaybackState.STATE_NONE, 0L));
        assertEquals(MediaCommandPolicy.Phase.READY,
                MediaCommandPolicy.phaseForController(PlaybackState.STATE_PAUSED,
                        PlaybackState.ACTION_PLAY));
        assertEquals(MediaCommandPolicy.Phase.PLAYING,
                MediaCommandPolicy.phaseForController(PlaybackState.STATE_PLAYING,
                        PlaybackState.ACTION_PAUSE));
    }

    @Test public void delaysNeverRestartTheOriginalDeadlineBudget() {
        assertEquals(900L, MediaCommandPolicy.boundedDelay(1000L, 5000L, 900L));
        assertEquals(250L, MediaCommandPolicy.boundedDelay(4750L, 5000L, 900L));
        assertEquals(0L, MediaCommandPolicy.boundedDelay(5100L, 5000L, 900L));
        assertEquals(0L, MediaCommandPolicy.remaining(5000L, 5000L));
    }

    @Test public void failedOrTimedOutRootSelectsNormalFallback() {
        assertEquals(MediaCommandPolicy.RootRoute.ROOT_SUCCEEDED,
                MediaCommandPolicy.routeAfterRoot(true));
        assertEquals(MediaCommandPolicy.RootRoute.NORMAL_FALLBACK,
                MediaCommandPolicy.routeAfterRoot(false));
    }

    @Test public void lateCallbacksCannotDispatchAfterCancellationOrDeadline() {
        assertTrue(MediaCommandPolicy.callbackStillCurrent(
                false, false, 7, 7, 1000L, 2000L));
        assertFalse(MediaCommandPolicy.callbackStillCurrent(
                true, false, 7, 7, 1000L, 2000L));
        assertFalse(MediaCommandPolicy.callbackStillCurrent(
                false, true, 7, 7, 1000L, 2000L));
        assertFalse(MediaCommandPolicy.callbackStillCurrent(
                false, false, 7, 8, 1000L, 2000L));
        assertFalse(MediaCommandPolicy.callbackStillCurrent(
                false, false, 7, 7, 2000L, 2000L));
    }

    @Test public void oppositeSourcePausesOnlyAfterNewPlayAcknowledgement() {
        assertFalse(MediaCommandPolicy.shouldCommitOppositePause(false, true, false));
        assertFalse(MediaCommandPolicy.shouldCommitOppositePause(true, false, false));
        assertFalse(MediaCommandPolicy.shouldCommitOppositePause(true, true, true));
        assertTrue(MediaCommandPolicy.shouldCommitOppositePause(true, true, false));
    }

    @Test public void duplicateColdToggleCoalescesBeforeDispatchOnly() {
        assertTrue(MediaCommandPolicy.shouldCoalesceToggle(
                MediaCommandPolicy.Desired.PLAY, false, false, MediaCommandPolicy.Desired.PLAY));
        assertFalse(MediaCommandPolicy.shouldCoalesceToggle(
                MediaCommandPolicy.Desired.PLAY, false, true, MediaCommandPolicy.Desired.PLAY));
        assertFalse(MediaCommandPolicy.shouldCoalesceToggle(
                MediaCommandPolicy.Desired.PLAY, true, false, MediaCommandPolicy.Desired.PLAY));
        assertFalse(MediaCommandPolicy.shouldCoalesceToggle(
                MediaCommandPolicy.Desired.PLAY, false, false, MediaCommandPolicy.Desired.PAUSE));
        assertFalse(MediaCommandPolicy.shouldCoalesceToggle(
                MediaCommandPolicy.Desired.NEXT, false, false, MediaCommandPolicy.Desired.NEXT));
    }
}
