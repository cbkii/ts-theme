package com.cbkii.ts18launcher;

import android.content.Context;

import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.platform.TopwayAdapter;

/** Persisted per-source engine choice and the exact configured package for each source. */
final class MediaEngineSelector {
    static final String KEY_MUSIC_ENGINE = "music.engine";
    static final String KEY_RADIO_ENGINE = "radio.engine";

    private MediaEngineSelector() {}

    static String prefKey(MediaSourceKey key) {
        return key == MediaSourceKey.RADIO ? KEY_RADIO_ENGINE : KEY_MUSIC_ENGINE;
    }

    /** Stored choice. Missing values select M2; malformed or future stored values select Legacy. */
    static MediaEngine requested(Context context, MediaSourceKey key) {
        try {
            return MediaEngine.fromStored(LauncherPrefs.prefs(context).getString(prefKey(key), "m2"));
        } catch (ClassCastException malformed) {
            return MediaEngine.LEGACY;
        }
    }

    static void setRequested(Context context, MediaSourceKey key, MediaEngine engine) {
        android.content.SharedPreferences.Editor editor = LauncherPrefs.prefs(context).edit();
        editor.putString(prefKey(key), engine.storedValue());
        editor.apply();
    }

    /** The exact configured package. Never a globally "current" or most-recent player. */
    static String configuredPackage(Context context, MediaSourceKey key) {
        if (key == MediaSourceKey.RADIO) return RadioProvider.resolvePackage(context);
        String music = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        return music.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : music;
    }

    static MediaSourceKey keyFor(String selectionSource) {
        return MediaSelection.RADIO.equals(selectionSource) ? MediaSourceKey.RADIO : MediaSourceKey.MUSIC;
    }
}
