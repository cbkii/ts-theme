package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RemovableMediaPolicyTest {
    @Test public void mountedIsAvailableAndMayWarmOnlyWhileHomeIsResumed() {
        RemovableMediaPolicy.Event event = RemovableMediaPolicy.classify(
                "android.intent.action.MEDIA_MOUNTED");
        assertEquals(RemovableMediaPolicy.Event.AVAILABLE, event);
        assertTrue(RemovableMediaPolicy.shouldWarm(event, true, true));
        assertFalse(RemovableMediaPolicy.shouldWarm(event, false, true));
        assertFalse(RemovableMediaPolicy.shouldWarm(event, true, false));
    }

    @Test public void removalEventsNeverRequestWarmup() {
        for (String action : new String[] {
                "android.intent.action.MEDIA_UNMOUNTED",
                "android.intent.action.MEDIA_EJECT",
                "android.intent.action.MEDIA_REMOVED",
                "android.intent.action.MEDIA_BAD_REMOVAL"
        }) {
            RemovableMediaPolicy.Event event = RemovableMediaPolicy.classify(action);
            assertEquals(RemovableMediaPolicy.Event.UNAVAILABLE, event);
            assertFalse(RemovableMediaPolicy.shouldWarm(event, true, true));
        }
    }

    @Test public void unrelatedBroadcastIsIgnored() {
        RemovableMediaPolicy.Event event = RemovableMediaPolicy.classify("android.intent.action.SCREEN_ON");
        assertEquals(RemovableMediaPolicy.Event.IGNORE, event);
        assertFalse(RemovableMediaPolicy.shouldWarm(event, true, true));
    }
}
