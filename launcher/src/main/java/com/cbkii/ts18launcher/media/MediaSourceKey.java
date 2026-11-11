package com.cbkii.ts18launcher.media;

/** The two independently configured HOME audio sources. */
public enum MediaSourceKey {
    MUSIC, RADIO;

    public MediaSourceKey opposite() { return this == MUSIC ? RADIO : MUSIC; }
}
