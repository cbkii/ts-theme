package com.cbkii.ts18launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationHelperResultTest {
    @Test public void parsesNativePresentationWithTransactionIdentity() {
        NavigationHelperResult result = NavigationHelperResult.parse(
                "OK code=PRESENTED_NATIVE user=10 task=9681 stack=13 package=app.organicmaps.incar "
                        + "component=app.organicmaps.incar/app.organicmaps.MwmActivity display=0 "
                        + "windowingMode=5 bounds=524,77,1174,453 supportsPip=0 "
                        + "launched=1 transaction=7 helpExit=255 helpWindowingMode=1 "
                        + "helpDisplay=1 launchExit=0");
        assertTrue(result.success);
        assertEquals(10, result.userId);
        assertEquals(9681, result.taskId);
        assertEquals(13, result.stackId);
        assertEquals(0, result.displayId);
        assertEquals(5, result.windowingMode);
        assertEquals(0, result.supportsPip);
        assertEquals(1, result.launched);
        assertEquals(7, result.transactionId);
        assertEquals(255, result.helpExit);
        assertEquals(1, result.helpWindowingMode);
        assertEquals(1, result.helpDisplay);
        assertEquals(0, result.launchExit);
        assertEquals("app.organicmaps.incar", result.packageName);
    }

    @Test public void parsesFailureWithObservedTaskAndUnknownComponent() {
        NavigationHelperResult result = NavigationHelperResult.parse(
                "FAIL code=BOUNDS_MISMATCH task=42 stack=3 package=com.example.nav "
                        + "component=unknown display=0 windowingMode=5 bounds=1,2,3,4 "
                        + "supportsPip=unknown launched=0 transaction=9");
        assertFalse(result.success);
        assertEquals("BOUNDS_MISMATCH", result.code);
        assertEquals(42, result.taskId);
        assertEquals("unknown", result.component);
        assertEquals(9, result.transactionId);
    }

    @Test public void preservesUnknownMetadata() {
        NavigationHelperResult result = NavigationHelperResult.parse(
                "OK code=STATUS task=42 stack=unknown package=com.example.nav "
                        + "component=com.example.nav/.Main display=unknown windowingMode=unknown "
                        + "bounds=unknown supportsPip=unknown launched=unknown transaction=unknown");
        assertTrue(result.success);
        assertEquals(-1, result.stackId);
        assertEquals(-1, result.displayId);
        assertEquals(-1, result.windowingMode);
        assertEquals(-1, result.supportsPip);
        assertEquals(-1, result.launched);
        assertEquals(-1, result.transactionId);
        assertEquals(-1, result.userId);
        assertEquals(-1, result.helpExit);
        assertEquals(-1, result.launchExit);
    }

    @Test public void rejectsNoise() {
        NavigationHelperResult result = NavigationHelperResult.parse("permission denied\nrandom output");
        assertFalse(result.success);
        assertEquals("BAD_RESPONSE", result.code);
    }
}
