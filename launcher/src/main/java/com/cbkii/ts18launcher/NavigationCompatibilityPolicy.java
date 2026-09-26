package com.cbkii.ts18launcher;

/** User-facing qualification status only; it never blocks selection or launch. */
final class NavigationCompatibilityPolicy {
    static final String GOOGLE_MAPS = "com.google.android.apps.maps";

    enum Tier { QUALIFIED, LIMITED, UNQUALIFIED }

    private NavigationCompatibilityPolicy() {}

    static Tier tier(String packageName) {
        if (NavigationProvider.ORGANIC_MAPS_INCAR.equals(packageName)
                || NavigationProvider.ORGANIC_MAPS.equals(packageName)) return Tier.QUALIFIED;
        if (GOOGLE_MAPS.equals(packageName)) return Tier.LIMITED;
        return Tier.UNQUALIFIED;
    }

    static String settingsMessage(String packageName, String mode) {
        if (packageName == null || packageName.isEmpty()) return "Choose a navigation app.";
        Tier tier = tier(packageName);
        if (tier == Tier.QUALIFIED) {
            return "Qualified for the TS18 launcher window contract. Physical app behaviour still applies.";
        }
        if (tier == Tier.LIMITED) {
            return "Not verified for launcher window mode. Google Maps is still allowed; fullscreen is the safer fallback if windowing misbehaves.";
        }
        if (HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(mode)) {
            return "This app has not been verified for launcher window mode. It will still be tried; use fullscreen if windowing fails.";
        }
        return "This navigation app is unverified on the TS18 but is not blocked.";
    }
}
