package com.cbkii.ts18launcher.media.direct;

import android.media.session.PlaybackState;

/**
 * One directional transport operation. There is deliberately no toggle. PREPARE asks the source
 * to load its current queue without playing; it is only sent passively and never as a user tap.
 */
public enum DirectOperation {
    PLAY, PAUSE, NEXT, PREVIOUS, PREPARE;

    /** Any one of these advertised action bits makes the operation supported. */
    long supportingActions() {
        switch (this) {
            case PLAY: return PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE;
            case PAUSE: return PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE;
            case NEXT: return PlaybackState.ACTION_SKIP_TO_NEXT;
            case PREVIOUS: return PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            default: return PlaybackState.ACTION_PREPARE;
        }
    }

    boolean directional() { return this == PLAY || this == PAUSE; }

    /** The framework state that confirms a directional operation. Skips have none. */
    boolean acknowledgedBy(int nativeState) {
        if (this == PLAY) return nativeState == PlaybackState.STATE_PLAYING;
        if (this == PAUSE) {
            return nativeState == PlaybackState.STATE_PAUSED
                    || nativeState == PlaybackState.STATE_STOPPED
                    || nativeState == PlaybackState.STATE_NONE;
        }
        return false;
    }

    /**
     * Resolve the Play/Pause button once, at tap time. A source that is playing, or still
     * buffering or connecting towards playback, is paused; anything else is played.
     */
    static DirectOperation resolvePlayPause(int nativeState) {
        return nativeState == PlaybackState.STATE_PLAYING
                || nativeState == PlaybackState.STATE_BUFFERING
                || nativeState == PlaybackState.STATE_CONNECTING ? PAUSE : PLAY;
    }
}
