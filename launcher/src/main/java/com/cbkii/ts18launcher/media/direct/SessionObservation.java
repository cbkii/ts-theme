package com.cbkii.ts18launcher.media.direct;

/**
 * One immutable reading of one live session, taken on a single framework event.
 *
 * Nothing here is carried over from an earlier reading: title, subtitle and item identity are
 * exactly what the owning session reported at that moment, so a new track or station can never
 * inherit the previous one's text.
 */
public final class SessionObservation {
    /** Stable identity of the framework session (the session token on device). */
    public static final String HOME_PLAY_V1 = "org.oxycblt.auxio.action.HOME_PLAY_V1";
    public final boolean deadlinePlay;
    public final boolean sourceOwned;
    public final Object token;
    public final String packageName;
    public final int nativeState;
    public final long actions;
    public final String itemId;
    public final String title;
    public final String subtitle;
    /** Source URI survives Auxio raw/primitive/canonical ID promotion. Never logged. */
    public final String mediaUri;
    public final long positionMs, stateUpdatedMs;

    public SessionObservation(Object token, String packageName, int nativeState, long actions,
                              String itemId, String title, String subtitle) {
        this(token, packageName, nativeState, actions, itemId, title, subtitle, false, false);
    }

    public SessionObservation(Object token, String packageName, int nativeState, long actions,
                              String itemId, String title, String subtitle,
                              boolean deadlinePlay, boolean sourceOwned) {
        this(token, packageName, nativeState, actions, itemId, title, subtitle,
                deadlinePlay, sourceOwned, "");
    }

    public SessionObservation(Object token, String packageName, int nativeState, long actions,
                              String itemId, String title, String subtitle,
                              boolean deadlinePlay, boolean sourceOwned, String mediaUri) {
        this(token, packageName, nativeState, actions, itemId, title, subtitle,
                deadlinePlay, sourceOwned, mediaUri, -1L, -1L);
    }

    public SessionObservation(Object token, String packageName, int nativeState, long actions,
                              String itemId, String title, String subtitle,
                              boolean deadlinePlay, boolean sourceOwned, String mediaUri,
                              long positionMs, long stateUpdatedMs) {
        this.deadlinePlay = deadlinePlay;
        this.sourceOwned = sourceOwned;
        this.token = token;
        this.packageName = packageName == null ? "" : packageName;
        this.nativeState = nativeState;
        this.actions = actions;
        this.itemId = itemId == null ? "" : itemId;
        this.title = title == null ? "" : title.trim();
        this.subtitle = subtitle == null ? "" : subtitle.trim();
        this.mediaUri = mediaUri == null || mediaUri.isEmpty()
                ? (this.itemId.startsWith("file:") || this.itemId.startsWith("content:") ? this.itemId : "") : mediaUri;
        this.positionMs = positionMs;
        this.stateUpdatedMs = stateUpdatedMs;
    }
    SessionObservation withSourceOwned(boolean owned) {
        return new SessionObservation(token, packageName, nativeState, actions, itemId, title,
                subtitle, deadlinePlay, owned, mediaUri, positionMs, stateUpdatedMs);
    }
}
