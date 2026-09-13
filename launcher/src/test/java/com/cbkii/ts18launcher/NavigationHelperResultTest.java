package com.cbkii.ts18launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class NavigationHelperResultTest {
 @Test public void parsesFreeformResultWithIdentity(){NavigationHelperResult r=NavigationHelperResult.parse("OK code=FREEFORM task=9681 stack=13 package=app.organicmaps.incar component=app.organicmaps.incar/app.organicmaps.MwmActivity display=0 windowingMode=5 bounds=524,77,1174,453 supportsPip=0 pinnedPackage=none");assertTrue(r.success);assertEquals(9681,r.taskId);assertEquals(13,r.stackId);assertEquals(0,r.displayId);assertEquals(5,r.windowingMode);assertEquals(0,r.supportsPip);assertEquals("app.organicmaps.incar",r.packageName);}
 @Test public void parsesPipOccupancyFailure(){NavigationHelperResult r=NavigationHelperResult.parse("FAIL code=PIP_OCCUPIED_BY_OTHER_APP package=app.organicmaps.incar pinnedPackage=com.example.video");assertFalse(r.success);assertEquals("PIP_OCCUPIED_BY_OTHER_APP",r.code);assertEquals("com.example.video",r.pinnedPackage);}
 @Test public void preservesUnknownMetadata(){NavigationHelperResult r=NavigationHelperResult.parse("OK code=STATUS task=42 stack=unknown package=com.example.nav component=com.example.nav/.Main display=unknown windowingMode=unknown bounds=unknown supportsPip=unknown pinnedPackage=none");assertTrue(r.success);assertEquals(-1,r.stackId);assertEquals(-1,r.displayId);assertEquals(-1,r.windowingMode);assertEquals(-1,r.supportsPip);}
 @Test public void rejectsNoise(){NavigationHelperResult r=NavigationHelperResult.parse("permission denied\nrandom output");assertFalse(r.success);assertEquals("BAD_RESPONSE",r.code);}
}
