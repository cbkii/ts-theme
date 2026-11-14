package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Single authority for the mutually-exclusive HOME navigation surfaces. */
final class HomeNavigationSurfacePolicy {
    static final String KEY = "navigation.surface.mode";
    static final String FULLSCREEN = "fullscreen";
    static final String NATIVE_WINDOW = "native_window";
    /** Retired in-HOME online map. Accepted only as upgrade/import input; never stored again. */
    static final String RETIRED_LEAFLET = "leaflet";
    /** Retired boolean that selected the online map before the surface-mode key existed. */
    static final String RETIRED_MAP_ENABLED_KEY = "map.enabled";
    /** Retired online-map option. Accepted in old exports and discarded. */
    static final String RETIRED_MAP_CONTROLS_KEY = "map.controls.enabled";
    private static final String LEGACY_RAW_FREEFORM = "raw_freeform";

    private HomeNavigationSurfacePolicy() {}

    static String mode(Context context) {
        SharedPreferences prefs = LauncherPrefs.prefs(context);
        boolean retiredMapEnabled = false;
        try {
            retiredMapEnabled = prefs.getBoolean(RETIRED_MAP_ENABLED_KEY, false);
        } catch (ClassCastException malformed) {
            // A malformed retired value is not a selection.
        }
        return resolve(prefs.contains(KEY), prefs.getString(KEY, ""), retiredMapEnabled);
    }

    /**
     * Pure resolution of stored state to a supported surface.
     *
     * The retired online map never maps to the root-managed native window: a user who selected it
     * had explicitly left the native window, so the conventional fullscreen launch is the only
     * surface that cannot surprise them. Clean installs keep the native window default.
     */
    static String resolve(boolean hasMode, String stored, boolean retiredMapEnabled) {
        if (hasMode) return canonical(stored);
        return retiredMapEnabled ? FULLSCREEN : NATIVE_WINDOW;
    }

    /** Maps any accepted stored/imported value to one of the two supported surfaces. */
    static String canonical(String value) {
        if (NATIVE_WINDOW.equals(value) || LEGACY_RAW_FREEFORM.equals(value)) return NATIVE_WINDOW;
        return FULLSCREEN;
    }

    static void setMode(Context context, String mode) {
        LauncherPrefs.prefs(context).edit()
                .putString(KEY, canonical(mode))
                .remove(RETIRED_MAP_ENABLED_KEY)
                .remove(RETIRED_MAP_CONTROLS_KEY)
                .apply();
    }

    /**
     * One idempotent upgrade step: persist the resolved surface and drop the retired keys so no
     * later reader can observe the online-map selection again.
     */
    static void migrateRetiredSurfaces(Context context) {
        SharedPreferences prefs = LauncherPrefs.prefs(context);
        boolean retiredKeys = prefs.contains(RETIRED_MAP_ENABLED_KEY)
                || prefs.contains(RETIRED_MAP_CONTROLS_KEY);
        boolean retiredMode = prefs.contains(KEY)
                && !canonical(prefs.getString(KEY, "")).equals(prefs.getString(KEY, ""));
        if (!retiredKeys && !retiredMode) return;
        String resolved = mode(context);
        SharedPreferences.Editor editor = prefs.edit()
                .remove(RETIRED_MAP_ENABLED_KEY).remove(RETIRED_MAP_CONTROLS_KEY);
        // Only write the authority when the user had one or had selected the retired map;
        // an untouched clean install keeps following the product default.
        if (prefs.contains(KEY) || FULLSCREEN.equals(resolved)) editor.putString(KEY, resolved);
        editor.apply();
    }

    /** Values accepted from storage or an imported configuration file. */
    static boolean isKnown(String value) {
        return FULLSCREEN.equals(value) || RETIRED_LEAFLET.equals(value)
                || NATIVE_WINDOW.equals(value) || LEGACY_RAW_FREEFORM.equals(value);
    }

    static String label(String value) {
        if (NATIVE_WINDOW.equals(canonical(value))) return "Native navigation window · TESTING";
        return "Fullscreen only · safe fallback";
    }
}
