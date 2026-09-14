package com.cbkii.ts18launcher;

import android.content.Context;

/** Compatibility seam for the legacy Leaflet code path; navigation-surface mode is authoritative. */
final class ExperimentalMapPolicy {
    private ExperimentalMapPolicy() {}

    static boolean enabled(Context context) {
        return HomeNavigationSurfacePolicy.LEAFLET.equals(HomeNavigationSurfacePolicy.mode(context));
    }
}
