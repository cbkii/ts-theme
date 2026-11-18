package com.cbkii.ts18launcher;

import static org.junit.Assert.assertNotEquals;

import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import org.junit.Test;

/** A failed bind is never presented as readiness (integration review F1). */
public class DirectFailurePresentationTest {
    @Test public void failedConnectionIsNotPresentedAsReadyToStart() {
        // Exactly what DirectSourceEngine.state() publishes after onStartFailed().
        MediaSourceState failed = MediaSourceState.unavailable(MediaSourceKey.MUSIC, MediaEngine.DIRECT,
                "com.tw.media", MediaSourceState.Availability.CONNECTION_FAILED, 1L).withStart(true, false, false);
        assertNotEquals("Press Play to start", DirectMediaPresentation.absentText(failed));
    }
}
