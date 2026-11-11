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

    /** One short line explaining why a direct source cannot be controlled; empty when it can. */
    static String unavailableText(MediaSourceState.Availability availability) {
        if (availability == null) return "";
        switch (availability) {
            case UNCONFIGURED: return "No app selected";
            case PERMISSION_NEEDED: return "Notification access needed";
            case NO_SESSION: return "Not running · open it once";
            case AMBIGUOUS_SOURCE: return "Several sessions · open the app";
            case SOURCE_LOST: return "Stopped · open it again";
            default: return "";
        }
    }
}
