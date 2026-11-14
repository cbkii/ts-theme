package com.cbkii.ts18launcher;

import com.cbkii.ts18launcher.media.MediaSourceState;

/**
 * Bounded continuity for one Direct (M2) source's now-playing text.
 *
 * A session token replacement or browser reconnect briefly reports the source as not live. The
 * last item of the same package stays visible (never as playing) for at most RETAIN_MS; after
 * that, or on any package change or unconfigured state, the absence is shown truthfully.
 * Framework-free and single-threaded; the caller supplies the clock.
 */
final class DirectMetadataRetention {
    static final long RETAIN_MS = 2000L;

    private MediaSourceState lastLive;
    private long lostAtMs = -1L;

    /**
     * The state to render for this observation. Returns the retained last live state while a
     * same-package gap is younger than RETAIN_MS, otherwise the observation itself.
     */
    MediaSourceState present(MediaSourceState observed, long nowMs) {
        if (observed == null) { clear(); return null; }
        if (observed.live()) {
            lastLive = observed;
            lostAtMs = -1L;
            return observed;
        }
        if (lastLive == null || observed.packageName.isEmpty()
                || !observed.packageName.equals(lastLive.packageName)
                || observed.availability == MediaSourceState.Availability.UNCONFIGURED) {
            clear();
            return observed;
        }
        if (lostAtMs < 0L) lostAtMs = nowMs;
        if (nowMs - lostAtMs >= RETAIN_MS) {
            clear();
            return observed;
        }
        // Text continuity only: never shown as playing, and no transport capability is implied.
        MediaSourceState kept = lastLive;
        return new MediaSourceState(kept.key, kept.engine, kept.packageName, MediaSourceState.Availability.LIVE,
                android.media.session.PlaybackState.STATE_PAUSED, 0L, kept.sessionIdentity, kept.itemId,
                kept.title, kept.subtitle, kept.generation);
    }

    /** Milliseconds until a retained item must be re-evaluated; -1 when nothing is retained. */
    long remainingMs(long nowMs) {
        if (lastLive == null || lostAtMs < 0L) return -1L;
        return Math.max(0L, RETAIN_MS - (nowMs - lostAtMs));
    }

    void clear() {
        lastLive = null;
        lostAtMs = -1L;
    }
}
