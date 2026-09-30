package com.cbkii.ts18launcher;

import android.content.Context;

/** Leaflet remains available as an explicit comparator, but new installs do not enable it implicitly. */
final class ExperimentalMapPolicy {
    private ExperimentalMapPolicy() {}

    static boolean enabled(Context context) {
        if (!LauncherPrefs.prefs(context).contains(LauncherPrefs.KEY_MAP_ENABLED)) return false;
        return LauncherPrefs.mapEnabled(context);
    }

    static void setEnabled(Context context, boolean enabled) {
        LauncherPrefs.setMapEnabled(context, enabled);
    }
}
