package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaStorageEventPolicyTest {
    @Test public void mountedIsAvailabilityReconciliationOnly() {
        assertTrue(MediaStorageEventPolicy.isMounted(MediaStorageEventPolicy.MEDIA_MOUNTED));
        assertTrue(MediaStorageEventPolicy.relevant(MediaStorageEventPolicy.MEDIA_MOUNTED));
        assertFalse(MediaStorageEventPolicy.isRemoval(MediaStorageEventPolicy.MEDIA_MOUNTED));
    }

    @Test public void removalEventsAreRecognisedWithoutClaimingQueueState() {
        assertTrue(MediaStorageEventPolicy.isRemoval(MediaStorageEventPolicy.MEDIA_UNMOUNTED));
        assertTrue(MediaStorageEventPolicy.isRemoval(MediaStorageEventPolicy.MEDIA_EJECT));
        assertTrue(MediaStorageEventPolicy.isRemoval(MediaStorageEventPolicy.MEDIA_REMOVED));
        assertFalse(MediaStorageEventPolicy.relevant("other"));
    }
}
