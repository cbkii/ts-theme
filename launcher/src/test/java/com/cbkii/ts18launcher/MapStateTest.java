package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapStateTest {
    @Test public void acceptsOnlyBoundedRecoverableViewport() {
        MapState state = new MapState();
        assertTrue(state.update(12, false, -35.3, 149.1));
        assertTrue(state.hasCentre);
        assertFalse(state.follow);
        assertFalse(state.update(99, true, -35.3, 149.1));
        assertFalse(state.update(12, true, 91, 149.1));
    }
}
