package com.cbkii.ts18launcher;

import android.os.SystemClock;

import com.cbkii.ts18launcher.media.MediaSourceState;

/** Driver-facing presentation of a source served by the direct (M2) engine. */
final class DirectMediaPresentation {
    private DirectMediaPresentation() {}

    /** Renderable snapshot of a live direct source; empty for every non-live state. */
    static MediaListenerService.Snapshot snapshot(MediaSourceState state) {
        if (state == null || !state.live()) return new MediaListenerService.Snapshot("", "", "", false);
        return new MediaListenerService.Snapshot(state.packageName, state.title, state.subtitle,
                state.nativeState, state.actions, state.sessionIdentity, state.itemId, -1L,
                state.generation, SystemClock.elapsedRealtime(), true, "DIRECT_SESSION", -1);
    }

    static boolean skipEnabled(MediaSourceState state, boolean next) {
        long action = next ? android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT
                : android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        return state != null && state.live() && (state.actions & action) != 0L;
    }

    static boolean playPauseEnabled(MediaSourceState state) {
        if (state == null) return false;
        if (state.startable || state.starting || state.preparing) return true;
        if (!state.live()) return false;
        // A newly offered session may still be publishing its actions. Keep the first Play
        // available so the bounded engine can wait for that capability instead of losing a tap.
        if (state.nativeState == android.media.session.PlaybackState.STATE_NONE) return true;
        long directional = MediaCommandPolicy.usesPauseAction(state.nativeState)
                ? android.media.session.PlaybackState.ACTION_PAUSE : android.media.session.PlaybackState.ACTION_PLAY;
        return (state.actions & (directional | android.media.session.PlaybackState.ACTION_PLAY_PAUSE)) != 0L;
    }

    /** One short line for a direct source that has no session: starting, startable or why not. */
    static String absentText(MediaSourceState state) {
        if (state == null) return "";
        if (state.starting) return "Starting…";
        if (state.preparing) return "Getting ready…";
        // A failed bind is not readiness: say so, while a fresh explicit attempt stays possible.
        if (state.availability == MediaSourceState.Availability.CONNECTION_FAILED)
            return "Not responding · tap Play to retry";
        if (state.startable) return "Press Play to start";
        return unavailableText(state.availability);
    }

    /** One short line explaining why a direct source cannot be controlled; empty when it can. */
    static String unavailableText(MediaSourceState.Availability availability) {
        if (availability == null) return "";
        switch (availability) {
            case UNCONFIGURED: return "No app selected";
            case PERMISSION_NEEDED: return "Notification access needed";
            case CONNECTION_FAILED: return "Media service connection failed";
            case NO_SESSION: return "Not running · open it once";
            case AMBIGUOUS_SOURCE: return "Several sessions · open the app";
            case SOURCE_LOST: return "Stopped · open it again";
            case SERVICE_UNAVAILABLE: return "Media service not answering";
            default: return "";
        }
    }
}
