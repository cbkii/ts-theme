package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Single authority for the mutually-exclusive HOME navigation-surface experiments. */
final class HomeNavigationSurfacePolicy {
    static final String KEY = "navigation.surface.mode";
    static final String FULLSCREEN = "fullscreen";
    static final String LEAFLET = "leaflet";
    static final String RAW_FREEFORM = "raw_freeform";
    static final String ANDROID_PIP = "android_pip";

    private HomeNavigationSurfacePolicy() {}

    static String mode(Context context) {
        SharedPreferences prefs = LauncherPrefs.prefs(context);
        String stored = prefs.getString(KEY, "");
        if (isKnown(stored)) return stored;
        if (prefs.contains(LauncherPrefs.KEY_MAP_ENABLED) && LauncherPrefs.mapEnabled(context)) return LEAFLET;
        return FULLSCREEN;
    }

    static void setMode(Context context, String mode) {
        String safe = isKnown(mode) ? mode : FULLSCREEN;
        LauncherPrefs.prefs(context).edit().putString(KEY, safe)
                .putBoolean(LauncherPrefs.KEY_MAP_ENABLED, LEAFLET.equals(safe)).apply();
    }

    static boolean isKnown(String value) {
        return FULLSCREEN.equals(value) || LEAFLET.equals(value)
                || RAW_FREEFORM.equals(value) || ANDROID_PIP.equals(value);
    }

    static String label(String value) {
        if (LEAFLET.equals(value)) return "Leaflet comparator";
        if (RAW_FREEFORM.equals(value)) return "Raw freeform task · experimental";
        if (ANDROID_PIP.equals(value)) return "Android PiP · experimental";
        return "Fullscreen only · safe fallback";
    }
}
