package com.cbkii.ts18launcher;

import android.media.session.PlaybackState;

/** Pure command/readiness rules shared by the Android media coordinator and JVM tests. */
final class MediaCommandPolicy {
    enum Desired { PLAY, PAUSE, PREVIOUS, NEXT }
    enum Phase { UNAVAILABLE, IDLE, STARTING, CONNECTED, READY, PLAYING, BLOCKED, FAILED }

    private MediaCommandPolicy() {}

    static Desired resolve(MediaListenerService.Command command, boolean playing) {
        if (command == MediaListenerService.Command.PREVIOUS) return Desired.PREVIOUS;
        if (command == MediaListenerService.Command.NEXT) return Desired.NEXT;
        return playing ? Desired.PAUSE : Desired.PLAY;
    }

    static boolean supports(Desired desired, long actions) {
        if (desired == null) return false;
        switch (desired) {
            case PREVIOUS:
                return (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L;
            case NEXT:
                return (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0L;
            case PLAY:
                return (actions & PlaybackState.ACTION_PLAY) != 0L
                        || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L;
            case PAUSE:
                return (actions & PlaybackState.ACTION_PAUSE) != 0L
                        || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L;
            default:
                return false;
        }
    }

    static boolean acknowledged(Desired desired, int state) {
        if (desired == Desired.PLAY) return usesPauseAction(state);
        if (desired == Desired.PAUSE) {
            return state == PlaybackState.STATE_PAUSED
                    || state == PlaybackState.STATE_STOPPED
                    || state == PlaybackState.STATE_NONE;
        }
        return true;
    }

    static boolean controllerReadyForPlay(int state, long actions) {
        return usesPauseAction(state) || supports(Desired.PLAY, actions);
    }

    static Phase phaseForController(int state, long actions) {
        if (usesPauseAction(state)) return Phase.PLAYING;
        if (controllerReadyForPlay(state, actions)) return Phase.READY;
        return Phase.CONNECTED;
    }

    static boolean usesPauseAction(int state) {
        return state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING;
    }

    static long remaining(long nowMs, long deadlineMs) {
        return Math.max(0L, deadlineMs - nowMs);
    }

    static long boundedDelay(long nowMs, long deadlineMs, long requestedMs) {
        return Math.max(0L, Math.min(requestedMs, remaining(nowMs, deadlineMs)));
    }
}
