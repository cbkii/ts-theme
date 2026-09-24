package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RootNavigationBackendTest {
    @Test public void fullscreenResizeTargetsPhysicalDisplayForSameTask() {
        String command = RootNavigationBackend.fullscreenResizeCommand(9257);
        assertTrue(command.contains("wm size"));
        assertTrue(command.contains("am task resizeable 9257 2"));
        assertTrue(command.contains("am task resize 9257 0 0"));
        assertTrue(command.contains("FULL_BOUNDS=0,0"));
    }

    @Test public void fullscreenBoundsParserIsStrict() {
        assertEquals("0,0,1280,720",
                RootNavigationBackend.parseFullBounds("noise\nFULL_BOUNDS=0,0,1280,720\n"));
        assertEquals("", RootNavigationBackend.parseFullBounds("FULL_BOUNDS=bad"));
    }
}
