package com.cbkii.ts18launcher;

import android.content.Context;

import com.cbkii.ts18launcher.media.MediaBackend;

/** Composition point for optional engines. */
final class MediaEngines {
    private MediaEngines() {}

    /**
     * The direct (M2) engine, or null when this build does not contain one. With null, a stored
     * request for the direct engine resolves to legacy and is never acted on.
     */
    static MediaBackend createDirect(Context context) {
        return null;
    }
}
