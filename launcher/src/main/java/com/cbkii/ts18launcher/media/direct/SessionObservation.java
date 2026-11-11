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
    public final Object token;
    public final String packageName;
    public final int nativeState;
    public final long actions;
    public final String itemId;
    public final String title;
    public final String subtitle;

    public SessionObservation(Object token, String packageName, int nativeState, long actions,
                              String itemId, String title, String subtitle) {
        this.token = token;
        this.packageName = packageName == null ? "" : packageName;
        this.nativeState = nativeState;
        this.actions = actions;
        this.itemId = itemId == null ? "" : itemId;
        this.title = title == null ? "" : title.trim();
        this.subtitle = subtitle == null ? "" : subtitle.trim();
    }
}
