package com.cbkii.ts18launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Narrow allowlist for optional root pre-grant of navigation runtime permissions. */
final class NavigationPermissionPolicy {
    static final String COARSE = "android.permission.ACCESS_COARSE_LOCATION";
    static final String FINE = "android.permission.ACCESS_FINE_LOCATION";
    static final String BACKGROUND = "android.permission.ACCESS_BACKGROUND_LOCATION";

    private static final List<String> ORDERED = Arrays.asList(COARSE, FINE, BACKGROUND);

    private NavigationPermissionPolicy() {}

    static List<String> missingDeclared(String[] requestedPermissions, Set<String> granted) {
        Set<String> requested = requestedPermissions == null
                ? java.util.Collections.emptySet()
                : new HashSet<>(Arrays.asList(requestedPermissions));
        Set<String> alreadyGranted = granted == null
                ? java.util.Collections.emptySet() : granted;
        List<String> result = new ArrayList<>();
        for (String permission : ORDERED) {
            if (requested.contains(permission) && !alreadyGranted.contains(permission)) {
                result.add(permission);
            }
        }
        return result;
    }

    static boolean isAllowed(String permission) {
        return ORDERED.contains(permission);
    }
}
