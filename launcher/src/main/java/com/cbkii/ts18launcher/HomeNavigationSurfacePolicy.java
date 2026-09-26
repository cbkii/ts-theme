package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Single authority for the mutually-exclusive HOME navigation surfaces. */
final class HomeNavigationSurfacePolicy {
    static final String KEY = "navigation.surface.mode";
    static final String FULLSCREEN = "fullscreen";
    static final String LEAFLET = "leaflet";
    static final String NATIVE_WINDOW = "native_window";
    private static final String LEGACY_RAW_FREEFORM = "raw_freeform";

    private HomeNavigationSurfacePolicy() {}

    static String mode(Context context) {
        SharedPreferences prefs = LauncherPrefs.prefs(context);
        if (prefs.contains(KEY)) {
            String stored = prefs.getString(KEY, "");
            if (LEGACY_RAW_FREEFORM.equals(stored)) return NATIVE_WINDOW;
            return isKnown(stored) ? stored : FULLSCREEN;
        }
        // Legacy PR #10 state is migration input only while the new authority is absent.
        if (prefs.contains(LauncherPrefs.KEY_MAP_ENABLED) && LauncherPrefs.mapEnabled(context)) {
            return LEAFLET;
        }
        return NATIVE_WINDOW;
    }

    static void setMode(Context context, String mode) {
        String safe = LEGACY_RAW_FREEFORM.equals(mode) ? NATIVE_WINDOW
                : (isKnown(mode) ? mode : FULLSCREEN);
        LauncherPrefs.prefs(context).edit()
                .putString(KEY, safe)
                .putBoolean(LauncherPrefs.KEY_MAP_ENABLED, LEAFLET.equals(safe))
                .apply();
    }

    static boolean isKnown(String value) {
        return FULLSCREEN.equals(value) || LEAFLET.equals(value)
                || NATIVE_WINDOW.equals(value) || LEGACY_RAW_FREEFORM.equals(value);
    }

    static String label(String value) {
        if (LEAFLET.equals(value)) return "Legacy online map fallback";
        if (NATIVE_WINDOW.equals(value) || LEGACY_RAW_FREEFORM.equals(value)) {
            return "Native navigation window · TESTING";
        }
        return "Fullscreen only · safe fallback";
    }
}
