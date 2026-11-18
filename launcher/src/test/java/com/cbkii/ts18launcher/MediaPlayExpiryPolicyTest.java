package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaCommandResult.Outcome;

import org.junit.Test;

/** Integration review F5: a sent Play that never played is reported, not left silent. */
public class MediaPlayExpiryPolicyTest {
    @Test public void sentPlayWithoutPlaybackIsAnExpiry() {
        assertTrue(new MediaCommandResult(Outcome.SENT_UNCONFIRMED, "", false, true).silentPlayExpiry());
        assertTrue(new MediaCommandResult(Outcome.SENT_UNCONFIRMED, "Play window expired", false, true)
                .silentPlayExpiry());
    }

    @Test public void driverSupersessionAndSuccessAreNotExpiries() {
        assertFalse(new MediaCommandResult(Outcome.SENT_UNCONFIRMED,
                "Superseded by a new playback direction", false, true).silentPlayExpiry());
        assertFalse(new MediaCommandResult(Outcome.ACKNOWLEDGED, "", true, true).silentPlayExpiry());
        assertFalse(new MediaCommandResult(Outcome.SENT_UNCONFIRMED, "", false, false).silentPlayExpiry());
    }
}
