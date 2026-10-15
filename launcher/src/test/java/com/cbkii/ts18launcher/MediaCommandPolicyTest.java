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

    @Test public void skipCommandsAreNeverPreAcknowledged() {
        assertFalse(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.PREVIOUS,
                PlaybackState.STATE_PLAYING));
        assertFalse(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.PREVIOUS,
                PlaybackState.STATE_PAUSED));
        assertFalse(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.NEXT,
                PlaybackState.STATE_PLAYING));
        assertFalse(MediaCommandPolicy.acknowledged(MediaCommandPolicy.Desired.NEXT,
                PlaybackState.STATE_NONE));
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
}
