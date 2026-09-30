package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NavigationCompatibilityPolicyTest {
    @Test public void organicMapsIsQualifiedButGoogleMapsRemainsAllowedWithWarning() {
        assertEquals(NavigationCompatibilityPolicy.Tier.QUALIFIED,
                NavigationCompatibilityPolicy.tier(NavigationProvider.ORGANIC_MAPS_INCAR));
        assertEquals(NavigationCompatibilityPolicy.Tier.LIMITED,
                NavigationCompatibilityPolicy.tier(NavigationProvider.GOOGLE_MAPS));
        assertTrue(NavigationCompatibilityPolicy.settingsMessage(
                NavigationProvider.GOOGLE_MAPS, HomeNavigationSurfacePolicy.NATIVE_WINDOW)
                .contains("still allowed"));
    }

    @Test public void unknownAppsAreWarnedNotBlocked() {
        assertEquals(NavigationCompatibilityPolicy.Tier.UNQUALIFIED,
                NavigationCompatibilityPolicy.tier("example.navigation"));
        assertTrue(NavigationCompatibilityPolicy.settingsMessage(
                "example.navigation", HomeNavigationSurfacePolicy.NATIVE_WINDOW)
                .contains("will still be tried"));
    }
}
