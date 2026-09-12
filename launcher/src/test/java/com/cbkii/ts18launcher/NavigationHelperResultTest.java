package com.cbkii.ts18launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationHelperResultTest {
    @Test public void parsesSuccessfulWindowResult() {
        NavigationHelperResult result = NavigationHelperResult.parse(
                "diagnostic line\nOK code=WINDOW task=9681 stack=13 package=app.organicmaps.incar "
                        + "component=app.organicmaps.incar/app.organicmaps.MwmActivity bounds=0,141,1131,702");
        assertTrue(result.success);
        assertEquals("WINDOW", result.code);
        assertEquals(9681, result.taskId);
        assertEquals(13, result.stackId);
        assertEquals("app.organicmaps.incar", result.packageName);
        assertEquals("app.organicmaps.incar/app.organicmaps.MwmActivity", result.component);
        assertEquals("0,141,1131,702", result.bounds);
    }

    @Test public void parsesFailureWithoutAdoptingTask() {
        NavigationHelperResult result = NavigationHelperResult.parse(
                "FAIL code=BOUNDS_MISMATCH task=9681 stack=13 package=app.organicmaps.incar "
                        + "bounds=524,77,1174,453 expected=0,141,1131,702");
        assertFalse(result.success);
        assertEquals("BOUNDS_MISMATCH", result.code);
        assertEquals(9681, result.taskId);
        assertEquals("app.organicmaps.incar", result.packageName);
    }

    @Test public void rejectsNoiseAsProtocolSuccess() {
        NavigationHelperResult result = NavigationHelperResult.parse("permission denied\nrandom output");
        assertFalse(result.success);
        assertEquals("BAD_RESPONSE", result.code);
        assertEquals(-1, result.taskId);
    }
}
