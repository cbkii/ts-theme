package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

public class NavigationPermissionPolicyTest {
    @Test public void grantsOnlyDeclaredMissingLocationPermissionsInStableOrder() {
        String[] requested = {
                "android.permission.CAMERA",
                NavigationPermissionPolicy.BACKGROUND,
                NavigationPermissionPolicy.FINE,
                NavigationPermissionPolicy.COARSE
        };
        assertEquals(Arrays.asList(
                        NavigationPermissionPolicy.COARSE,
                        NavigationPermissionPolicy.FINE,
                        NavigationPermissionPolicy.BACKGROUND),
                NavigationPermissionPolicy.missingDeclared(requested, Collections.emptySet()));
    }

    @Test public void alreadyGrantedAndUnrelatedPermissionsAreExcluded() {
        String[] requested = {
                NavigationPermissionPolicy.COARSE,
                NavigationPermissionPolicy.FINE,
                "android.permission.READ_CONTACTS"
        };
        assertEquals(Collections.singletonList(NavigationPermissionPolicy.FINE),
                NavigationPermissionPolicy.missingDeclared(requested,
                        new HashSet<>(Collections.singletonList(NavigationPermissionPolicy.COARSE))));
    }
}
