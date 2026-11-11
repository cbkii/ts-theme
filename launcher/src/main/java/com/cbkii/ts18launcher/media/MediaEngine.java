package com.cbkii.ts18launcher.media;

/**
 * Control engine selected independently for Music and for Radio.
 *
 * {@link #LEGACY} (M1) is the original observer/bootstrapper machinery and stays the default and
 * rollback. {@link #DIRECT} (M2) is the separate exact-source engine. Unknown, missing or future
 * stored values always resolve to {@link #LEGACY}, so a downgrade can never strand a source.
 */
public enum MediaEngine {
    LEGACY("legacy"), DIRECT("m2");

    private final String stored;

    MediaEngine(String stored) { this.stored = stored; }

    public String storedValue() { return stored; }

    public static MediaEngine fromStored(String value) {
        return DIRECT.stored.equals(value) ? DIRECT : LEGACY;
    }

    public static boolean isKnownStoredValue(String value) {
        return LEGACY.stored.equals(value) || DIRECT.stored.equals(value);
    }
}
