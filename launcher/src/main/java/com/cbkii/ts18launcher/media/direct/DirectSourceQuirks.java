package com.cbkii.ts18launcher.media.direct;

import android.media.session.PlaybackState;

/**
 * Exact-source capability additions for the direct engine.
 *
 * A session's advertised actions are the default authority. The two sources below are known to
 * accept Next and Previous while not always advertising them, so the direct engine sends those
 * skips to them on request. Nothing else is assumed for any package.
 */
final class DirectSourceQuirks {
    private static final String AUXIO_TS = "com.tw.media";
    private static final String NAVRADIO = "com.navimods.radio";

    private DirectSourceQuirks() {}

    static long extraActions(String packageName) {
        if (AUXIO_TS.equals(packageName) || NAVRADIO.equals(packageName)) {
            return PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        }
        return 0L;
    }
}
