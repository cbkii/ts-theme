package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

/** Compatibility seam for PR #10's Leaflet switch; navigation-surface mode is the authority. */
final class ExperimentalMapPolicy {
    private ExperimentalMapPolicy() {}

    static boolean enabled(Context context) {
        if (!LauncherPrefs.prefs(context).contains(HomeNavigationSurfacePolicy.KEY))
            return legacyLeafletExplicitlyEnabled(context);
        return HomeNavigationSurfacePolicy.LEAFLET.equals(HomeNavigationSurfacePolicy.mode(context));
    }

    /** Historical boolean is consulted only to migrate installs that have no new mode preference. */
    private static boolean legacyLeafletExplicitlyEnabled(Context context) {
        if (!LauncherPrefs.prefs(context).contains(LauncherPrefs.KEY_MAP_ENABLED)) return false;
        return LauncherPrefs.mapEnabled(context);
    }

    static void setEnabled(Context context, boolean ignoredCheckedState) {
        if (!(context instanceof Activity)) {
            HomeNavigationSurfacePolicy.setMode(context, HomeNavigationSurfacePolicy.FULLSCREEN);
            return;
        }
        Activity activity = (Activity) context;
        String[] labels = {
                "Fullscreen only · safe fallback",
                "Leaflet comparator",
                "Raw freeform task · experimental",
                "Android PiP · experimental"
        };
        String[] values = {
                HomeNavigationSurfacePolicy.FULLSCREEN,
                HomeNavigationSurfacePolicy.LEAFLET,
                HomeNavigationSurfacePolicy.RAW_FREEFORM,
                HomeNavigationSurfacePolicy.ANDROID_PIP
        };
        String current = HomeNavigationSurfacePolicy.mode(context);
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        new AlertDialog.Builder(activity)
                .setTitle("HOME navigation surface")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    HomeNavigationSurfacePolicy.setMode(activity, values[which]);
                    dialog.dismiss();
                    activity.recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
