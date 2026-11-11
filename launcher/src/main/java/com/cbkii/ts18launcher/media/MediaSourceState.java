package com.cbkii.ts18launcher.media;

/**
 * Immutable, UI-facing state of one configured source as reported by its selected engine.
 *
 * nativeState and actions carry the framework PlaybackState values unchanged, so
 * BUFFERING/CONNECTING are never presented as PLAYING. itemId is separate from
 * sessionIdentity: a new track or station under the same session is a new item.
 */
public final class MediaSourceState {
    public enum Availability {
        /** No package is configured for this source. */
        UNCONFIGURED,
        /** Notification-listener access is missing; sessions cannot be observed. */
        PERMISSION_NEEDED,
        /** The configured package has no live session. */
        NO_SESSION,
        /** Several sessions of the configured package exist and none can be chosen safely. */
        AMBIGUOUS_SOURCE,
        /** The session that was being controlled has gone. */
        SOURCE_LOST,
        /** Exactly one live session of the configured package is pinned. */
        LIVE
    }

    /** Framework PlaybackState.STATE_NONE; kept local so this type stays free of Android classes. */
    public static final int NATIVE_STATE_NONE = 0;
    /** Framework PlaybackState.STATE_PLAYING. */
    public static final int NATIVE_STATE_PLAYING = 3;

    public final MediaSourceKey key;
    public final MediaEngine engine;
    public final String packageName;
    public final Availability availability;
    public final int nativeState;
    public final long actions;
    public final Object sessionIdentity;
    public final String itemId;
    public final String title;
    public final String subtitle;
    public final long generation;

    public MediaSourceState(MediaSourceKey key, MediaEngine engine, String packageName,
                            Availability availability, int nativeState, long actions,
                            Object sessionIdentity, String itemId, String title, String subtitle,
                            long generation) {
        this.key = key;
        this.engine = engine;
        this.packageName = packageName == null ? "" : packageName;
        this.availability = availability;
        this.nativeState = availability == Availability.LIVE ? nativeState : NATIVE_STATE_NONE;
        this.actions = availability == Availability.LIVE ? actions : 0L;
        this.sessionIdentity = availability == Availability.LIVE ? sessionIdentity : null;
        this.itemId = clean(availability == Availability.LIVE ? itemId : "");
        this.title = clean(availability == Availability.LIVE ? title : "");
        this.subtitle = clean(availability == Availability.LIVE ? subtitle : "");
        this.generation = generation;
    }

    public static MediaSourceState unavailable(MediaSourceKey key, MediaEngine engine,
                                               String packageName, Availability availability,
                                               long generation) {
        return new MediaSourceState(key, engine, packageName, availability, NATIVE_STATE_NONE, 0L,
                null, "", "", "", generation);
    }

    public boolean live() { return availability == Availability.LIVE; }

    /** Only the framework's own PLAYING state counts; buffering and connecting do not. */
    public boolean playing() { return live() && nativeState == NATIVE_STATE_PLAYING; }

    /** Presentation equality: identical states must not restart tickers or animations. */
    public boolean samePresentation(MediaSourceState other) {
        return other != null && key == other.key && engine == other.engine
                && availability == other.availability && nativeState == other.nativeState
                && actions == other.actions && packageName.equals(other.packageName)
                && itemId.equals(other.itemId) && title.equals(other.title)
                && subtitle.equals(other.subtitle)
                && (sessionIdentity == null ? other.sessionIdentity == null
                        : sessionIdentity.equals(other.sessionIdentity));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    @Override public String toString() {
        return key + "/" + engine + " " + packageName + " " + availability + " state=" + nativeState
                + " actions=" + actions + " item=" + itemId + " gen=" + generation;
    }
}
