package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RootNavigationBackendTest {
    @Test public void warmHomeFocusTargetsExactTaskWithoutNavigationRelaunchOrResize() {
        String command = NavigationRootHelper.homeFocusCommand(9257);
        assertTrue(command.contains("/system/bin/am task focus 9257"));
        assertTrue(command.contains("PATH=/system/bin:/system/xbin:/vendor/bin"));
        assertFalse(command.contains("am start"));
        assertFalse(command.contains("am task resize"));
    }
}
