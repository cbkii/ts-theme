package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import android.media.session.PlaybackState;

import org.junit.Test;

public final class MediaMetadataStabilityTest {
    @Test public void transientEmptyMetadataKeepsLastValidTextForSameLiveSource() {
        MediaListenerService.Snapshot previous = new MediaListenerService.Snapshot(
                "com.tw.media", "Track", "Artist", PlaybackState.STATE_PLAYING, 0L);
        MediaListenerService.Snapshot next = new MediaListenerService.Snapshot(
                "com.tw.media", "", "", PlaybackState.STATE_PLAYING, 0L);
        MediaListenerService.Snapshot stable = MediaListenerService.stabiliseSnapshot(previous, next);
        assertEquals("Track", stable.title);
        assertEquals("Artist", stable.artist);
    }

    @Test public void genuineSessionRemovalStillClearsMetadata() {
        MediaListenerService.Snapshot previous = new MediaListenerService.Snapshot(
                "com.tw.media", "Track", "Artist", PlaybackState.STATE_PLAYING, 0L);
        MediaListenerService.Snapshot stable = MediaListenerService.stabiliseSnapshot(
                previous, new MediaListenerService.Snapshot("", "", "", false));
        assertEquals("", stable.packageName);
        assertEquals("", stable.title);
    }
}
