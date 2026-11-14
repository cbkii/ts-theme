package com.cbkii.ts18launcher;

/**
 * What the navigation task's top Activity says about it. Framework-free; shared by the root
 * broker (NavTaskBridge) and the HOME controller so both classify identically.
 */
final class NavigationTaskStage {
    static final String MAP = "MAP";
    static final String BOOTSTRAP_PENDING = "BOOTSTRAP_PENDING";
    static final String USER_ACTIVITY = "USER_ACTIVITY";
    private static final String MAPS = "app.organicmaps.incar/app.organicmaps.";

    private NavigationTaskStage() {}

    /**
     * MAP for the map Activity; BOOTSTRAP_PENDING only for the splash/resource-setup entries; any
     * other Organic Maps Activity on top (settings, downloader, bookmarks, editor, ...) is the
     * driver using Maps itself: USER_ACTIVITY, a healthy task that must not be repaired or latched.
     */
    static String of(String component) {
        if ((MAPS + "MwmActivity").equals(component)) return MAP;
        if ((MAPS + "SplashActivity").equals(component)
                || (MAPS + "DownloadResourcesActivity").equals(component)
                || (MAPS + "DownloadResourcesLegacyActivity").equals(component)) return BOOTSTRAP_PENDING;
        return component != null && component.startsWith("app.organicmaps.incar/") ? USER_ACTIVITY : BOOTSTRAP_PENDING;
    }
}
