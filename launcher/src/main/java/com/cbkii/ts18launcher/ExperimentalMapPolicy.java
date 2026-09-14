package com.cbkii.ts18launcher;

import android.content.Context;

/** Compatibility seam for the legacy Leaflet code path; navigation-surface mode is authoritative. */
final class ExperimentalMapPolicy {
    private ExperimentalMapPolicy() {}

    static boolean enabled(Context context) {
        if (!LauncherPrefs.prefs(context).contains(HomeNavigationSurfacePolicy.KEY)) {
            return legacyLeafletExplicitlyEnabled(context);
        }
        return HomeNavigationSurfacePolicy.LEAFLET.equals(HomeNavigationSurfacePolicy.mode(context));
    }

    /** Historical boolean is migration input only when the new authority has never been written. */
    private static boolean legacyLeafletExplicitlyEnabled(Context context) {
        if (!LauncherPrefs.prefs(context).contains(LauncherPrefs.KEY_MAP_ENABLED)) return false;
        return LauncherPrefs.mapEnabled(context);
    }
}
